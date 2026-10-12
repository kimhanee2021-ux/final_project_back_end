package org.ict.final_project_back_end.domain.user.service;

import org.ict.final_project_back_end.domain.user.dto.AvailabilityResponse;
import org.ict.final_project_back_end.domain.user.dto.LoginRequest;
import org.ict.final_project_back_end.domain.user.dto.SignupRequest;
import org.ict.final_project_back_end.domain.user.dto.TokenResponse;
import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.DuplicateEmailException;
import org.ict.final_project_back_end.global.exception.DuplicateNicknameException;
import org.ict.final_project_back_end.global.exception.InvalidCredentialsException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.ict.final_project_back_end.global.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Override
  public AvailabilityResponse checkEmailAvailability(String email) {
    boolean available = !isBlank(email) && !userRepository.existsByEmail(email);
    return new AvailabilityResponse(available);
  }

  @Override
  public AvailabilityResponse checkNicknameAvailability(String nickname) {
    boolean available = !isBlank(nickname) && !userRepository.existsByNickname(nickname);
    return new AvailabilityResponse(available);
  }

  @Override
  @Transactional
  public TokenResponse signup(SignupRequest request) {
    if (isBlank(request.email()) || isBlank(request.password()) || isBlank(request.nickname())) {
      throw new InvalidRequestException("이메일, 닉네임, 비밀번호를 모두 입력해주세요");
    }
    if (userRepository.existsByEmail(request.email())) {
      throw new DuplicateEmailException();
    }
    if (userRepository.existsByNickname(request.nickname())) {
      throw new DuplicateNicknameException();
    }
    User user = userRepository.save(User.builder()
        .email(request.email())
        .nickname(request.nickname())
        .passwordHash(passwordEncoder.encode(request.password()))
        .build());
    return new TokenResponse(jwtService.generateToken(user.getId(), user.getEmail()));
  }

  @Override
  public TokenResponse login(LoginRequest request) {
    // password가 null이면 BCrypt의 matches()가 예외를 던져 500이 나므로 먼저 막는다
    if (isBlank(request.email()) || isBlank(request.password())) {
      throw new InvalidRequestException("이메일과 비밀번호를 모두 입력해주세요");
    }
    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new InvalidCredentialsException(InvalidCredentialsException.INVALID_CREDENTIALS, "가입되지 않은 이메일입니다"));
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException(InvalidCredentialsException.INVALID_CREDENTIALS, "비밀번호가 일치하지 않습니다");
    }
    return new TokenResponse(jwtService.generateToken(user.getId(), user.getEmail()));
  }

  @Override
  public UserResponse getCurrentUser(Long userId) {
    return userRepository.findById(userId)
        .map(UserResponse::from)
        .orElseThrow(UserNotFoundException::new);
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
