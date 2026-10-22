package org.ict.final_project_back_end.domain.user.service;

import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.purchase.repository.GameLicenseRepository;
import org.ict.final_project_back_end.domain.purchase.repository.PurchaseTransactionRepository;
import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.ict.final_project_back_end.domain.user.dto.UserUpdateRequest;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.DuplicateNicknameException;
import org.ict.final_project_back_end.global.exception.InvalidCredentialsException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final PurchaseTransactionRepository purchaseTransactionRepository;
  private final GameLicenseRepository gameLicenseRepository;
  private final PasswordEncoder passwordEncoder;

  public UserServiceImpl(UserRepository userRepository,
                         LibraryGameRepository libraryGameRepository,
                         PurchaseTransactionRepository purchaseTransactionRepository,
                         GameLicenseRepository gameLicenseRepository,
                         PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.purchaseTransactionRepository = purchaseTransactionRepository;
    this.gameLicenseRepository = gameLicenseRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public UserResponse getUserInfo(Long userId) {
    return UserResponse.from(findUser(userId));
  }

  // 변경 감지(dirty checking)로 저장되므로 별도 save 호출은 하지 않는다.
  @Override
  @Transactional
  public UserResponse updateUserInfo(Long userId, UserUpdateRequest request) {
    User user = findUser(userId);

    if (request.nickname() != null) {
      if (isBlank(request.nickname())) {
        throw new InvalidRequestException("닉네임을 입력해주세요");
      }
      if (!request.nickname().equals(user.getNickname())) {
        if (userRepository.existsByNickname(request.nickname())) {
          throw new DuplicateNicknameException();
        }
        user.setNickname(request.nickname());
      }
    }

    if (request.newPassword() != null) {
      if (isBlank(request.newPassword())) {
        throw new InvalidRequestException("새 비밀번호를 입력해주세요");
      }
      // 토큰만 탈취당한 경우 비밀번호가 바뀌지 않도록 현재 비밀번호를 한 번 더 확인한다
      if (isBlank(request.currentPassword())) {
        throw new InvalidRequestException("현재 비밀번호를 입력해주세요");
      }
      if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
        throw new InvalidCredentialsException(InvalidCredentialsException.INVALID_CREDENTIALS, "현재 비밀번호가 일치하지 않습니다");
      }
      user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    return UserResponse.from(user);
  }

  // LibraryGame, 가져온 구매내역(purchase_transactions)과 라이선스(game_licenses)가 users를 FK로 참조하므로 먼저 지워야 사용자 삭제가 성공한다.
  @Override
  @Transactional
  public void deleteUser(Long userId) {
    User user = findUser(userId);
    libraryGameRepository.deleteByUserId(userId);
    gameLicenseRepository.deleteByUserId(userId);
    purchaseTransactionRepository.deleteItemsByUserId(userId);
    purchaseTransactionRepository.deleteTransactionsByUserId(userId);
    userRepository.delete(user);
  }

  private User findUser(Long userId) {
    return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
