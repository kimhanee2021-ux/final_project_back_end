package org.ict.final_project_back_end.domain.user.service;

import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.ict.final_project_back_end.domain.user.dto.UserUpdateRequest;

// 로그인한 사용자 본인의 정보를 조회·수정·삭제하는 서비스.
// 인증 관련(회원가입/로그인)은 AuthService가 담당한다.
public interface UserService {

  UserResponse getUserInfo(Long userId);

  UserResponse updateUserInfo(Long userId, UserUpdateRequest request);

  void deleteUser(Long userId);

  // 스팀 연동을 끊는다. 그 스팀 계정에서 동기화한 보유 게임은 지우고, 직접 가져온 구매내역은 남긴다.
  void unlinkSteam(Long userId);
}
