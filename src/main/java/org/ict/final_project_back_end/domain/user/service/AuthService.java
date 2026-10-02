package org.ict.final_project_back_end.domain.user.service;

import org.ict.final_project_back_end.domain.user.dto.AvailabilityResponse;
import org.ict.final_project_back_end.domain.user.dto.LoginRequest;
import org.ict.final_project_back_end.domain.user.dto.SignupRequest;
import org.ict.final_project_back_end.domain.user.dto.TokenResponse;
import org.ict.final_project_back_end.domain.user.dto.UserResponse;

// 이메일/비밀번호 기반 인증(회원가입, 로그인, 중복확인, 내 정보 조회)을 담당하는 서비스.
// 컨트롤러는 이 인터페이스에만 의존하므로 구현 방식이 바뀌어도 컨트롤러는 영향받지 않는다.
public interface AuthService {

  AvailabilityResponse checkEmailAvailability(String email);

  AvailabilityResponse checkNicknameAvailability(String nickname);

  TokenResponse signup(SignupRequest request);

  TokenResponse login(LoginRequest request);

  UserResponse getCurrentUser(Long userId);
}
