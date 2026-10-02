package org.ict.final_project_back_end.domain.user.controller;

import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

// 이메일/비밀번호 방식의 회원가입·로그인 API. 성공하면 JwtService로 JWT를 발급해 응답한다.
@RestController
@RequestMapping("/api")
public class AuthController {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public record SignupRequest(String email, String nickname, String password) {
  }

  public record LoginRequest(String email, String password) {
  }

  // 회원가입 폼에서 입력하는 즉시(제출 전) 중복 여부를 바로 확인하기 위한 API
  @GetMapping("/auth/check-email")
  public ResponseEntity<?> checkEmail(@RequestParam String email) {
    boolean available = !isBlank(email) && !userRepository.existsByEmail(email);
    return ResponseEntity.ok(Map.of("available", available));
  }

  @GetMapping("/auth/check-nickname")
  public ResponseEntity<?> checkNickname(@RequestParam String nickname) {
    boolean available = !isBlank(nickname) && !userRepository.existsByNickname(nickname);
    return ResponseEntity.ok(Map.of("available", available));
  }

  // 이메일 회원가입: 비밀번호는 passwordEncoder로 해싱해서 저장하고,
  // 가입 직후 바로 로그인된 상태로 만들기 위해 JWT까지 함께 발급해서 응답한다.
  @PostMapping("/auth/signup")
  public ResponseEntity<?> signup(@RequestBody SignupRequest req) {
    if (isBlank(req.email()) || isBlank(req.password()) || isBlank(req.nickname())) {
      return ResponseEntity.badRequest().body(Map.of("error", "이메일, 닉네임, 비밀번호를 모두 입력해주세요"));
    }
    if (userRepository.existsByEmail(req.email())) {
      return ResponseEntity.status(409).body(Map.of("error", "이미 가입된 이메일입니다"));
    }
    if (userRepository.existsByNickname(req.nickname())) {
      return ResponseEntity.status(409).body(Map.of("error", "이미 사용 중인 닉네임입니다"));
    }
    User user = userRepository.save(User.builder()
        .email(req.email())
        .nickname(req.nickname())
        .passwordHash(passwordEncoder.encode(req.password()))
        .build());
    String token = jwtService.generateToken(user.getId(), user.getEmail());
    return ResponseEntity.ok(Map.of("token", token));
  }

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req) {
    // password가 null이면 BCrypt의 matches()가 예외를 던져 500이 나므로 먼저 막는다
    if (isBlank(req.email()) || isBlank(req.password())) {
      return ResponseEntity.badRequest().body(Map.of("error", "이메일과 비밀번호를 모두 입력해주세요"));
    }
    Optional<User> userOpt = userRepository.findByEmail(req.email());
    if (userOpt.isEmpty()) {
      return ResponseEntity.status(401).body(Map.of("error", "가입되지 않은 이메일입니다"));
    }
    User user = userOpt.get();
    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      return ResponseEntity.status(401).body(Map.of("error", "비밀번호가 일치하지 않습니다"));
    }
    String token = jwtService.generateToken(user.getId(), user.getEmail());
    return ResponseEntity.ok(Map.of("token", token));
  }

  // 로그인 상태 확인용 - JwtAuthFilter가 SecurityContext에 넣어둔 userId를 그대로 꺼내 쓴다.
  // /api/** 는 인증 필수라 토큰이 없으면 여기까지 오지 않고 401이 나간다.
  @GetMapping("/me")
  public ResponseEntity<?> me(@AuthenticationPrincipal Long userId) {
    return userRepository.findById(userId)
        .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "email", user.getEmail(),
            "nickname", user.getNickname())))
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "존재하지 않는 회원입니다")));
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
