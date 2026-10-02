package org.ict.final_project_back_end.domain.user.controller;

import org.ict.final_project_back_end.domain.user.dto.AvailabilityResponse;
import org.ict.final_project_back_end.domain.user.dto.LoginRequest;
import org.ict.final_project_back_end.domain.user.dto.SignupRequest;
import org.ict.final_project_back_end.domain.user.dto.TokenResponse;
import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.ict.final_project_back_end.domain.user.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 이메일/비밀번호 방식의 회원가입·로그인 API. 실제 로직은 AuthService에 위임하고,
// 이 컨트롤러는 HTTP 요청/응답 매핑만 담당한다.
@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  // 회원가입 폼에서 입력하는 즉시(제출 전) 중복 여부를 바로 확인하기 위한 API
  @GetMapping("/auth/check-email")
  public ResponseEntity<AvailabilityResponse> checkEmail(@RequestParam String email) {
    return ResponseEntity.ok(authService.checkEmailAvailability(email));
  }

  @GetMapping("/auth/check-nickname")
  public ResponseEntity<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
    return ResponseEntity.ok(authService.checkNicknameAvailability(nickname));
  }

  // 이메일 회원가입: 가입 직후 바로 로그인된 상태로 만들기 위해 JWT까지 함께 발급해서 응답한다.
  @PostMapping("/auth/signup")
  public ResponseEntity<TokenResponse> signup(@RequestBody SignupRequest request) {
    return ResponseEntity.ok(authService.signup(request));
  }

  @PostMapping("/auth/login")
  public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  // 로그인 상태 확인용 - JwtAuthFilter가 SecurityContext에 넣어둔 userId를 그대로 꺼내 쓴다.
  // /api/** 는 인증 필수라 토큰이 없으면 여기까지 오지 않고 401이 나간다.
  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(authService.getCurrentUser(userId));
  }
}
