package org.ict.final_project_back_end.domain.purchase.service;

import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportResponse;
import org.ict.final_project_back_end.domain.purchase.entity.GameLicense;
import org.ict.final_project_back_end.domain.purchase.repository.GameLicenseRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.exception.UserNotFoundException;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 스팀 라이선스 페이지 가져오기. 금액이 없어서 환율·스팀 확인 없이 내 보유 게임 이름으로만 연결한다.
@Service
public class LicenseImportServiceImpl implements LicenseImportService {

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final GameLicenseRepository licenseRepository;

  public LicenseImportServiceImpl(UserRepository userRepository, LibraryGameRepository libraryGameRepository,
                                  GameLicenseRepository licenseRepository) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.licenseRepository = licenseRepository;
  }

  @Override
  public LicenseImportResponse importLicenses(Long userId, LicenseImportRequest request) {
    if (userId == null) {
      throw new AuthenticationRequiredException();
    }
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    Set<String> existingKeys = licenseRepository.findExternalKeysByUserId(userId);
    Map<String, Long> library = GameNameMatcher.libraryByName(libraryGameRepository.findAllByUserId(userId));

    Set<String> seen = new HashSet<>();
    List<GameLicense> toSave = new ArrayList<>();
    int duplicates = 0;
    int matched = 0;
    for (LicenseImportRequest.License license : request.licenses()) {
      if (existingKeys.contains(license.externalKey()) || !seen.add(license.externalKey())) {
        duplicates++;
        continue;
      }
      Long appId = GameNameMatcher.matchOwnedLicense(license.name(), library);
      if (appId != null) {
        matched++;
      }
      toSave.add(GameLicense.builder()
          .user(user)
          .externalKey(license.externalKey())
          .rawName(license.name())
          .appId(appId)
          .acquiredAt(license.acquiredAt())
          .method(license.method())
          .freeLicense(license.freeLicense())
          .build());
    }
    licenseRepository.saveAll(toSave);
    return new LicenseImportResponse(toSave.size(), duplicates, matched);
  }
}
