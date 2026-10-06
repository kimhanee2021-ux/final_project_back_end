package org.ict.final_project_back_end.domain.library.controller;

import org.ict.final_project_back_end.domain.library.dto.LibraryGameResponse;
import org.ict.final_project_back_end.domain.library.service.LibraryService;
import org.ict.final_project_back_end.global.swagger.LibraryApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 스팀 보유 게임 라이브러리 API. 실제 로직은 LibraryService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/library")
public class LibraryController implements LibraryApiDocs {

  private final LibraryService libraryService;

  public LibraryController(LibraryService libraryService) {
    this.libraryService = libraryService;
  }

  // 로그인한 사용자의 연동된 스팀 계정 기준으로 보유 게임을 동기화한다. 인증 정보는 JwtAuthFilter가 넣은 userId를 사용.
  @Override
  @PostMapping("/steam/sync")
  public ResponseEntity<List<LibraryGameResponse>> syncSteamLibrary(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(libraryService.syncSteamLibrary(userId));
  }
}
