package org.ict.final_project_back_end.domain.user.controller;

import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.ict.final_project_back_end.domain.user.dto.UserUpdateRequest;
import org.ict.final_project_back_end.domain.user.service.UserService;
import org.ict.final_project_back_end.global.swagger.UserApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 정보를 조회·수정·삭제하는 API. 실제 로직은 UserService에 위임한다.
// 대상은 항상 JWT의 userId(/me)이므로 다른 사용자의 정보에는 접근할 수 없다.
@RestController
@RequestMapping("/api/users/me")
public class UserController implements UserApiDocs {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @Override
  @GetMapping
  public ResponseEntity<UserResponse> getMyInfo(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(userService.getUserInfo(userId));
  }

  @Override
  @PatchMapping
  public ResponseEntity<UserResponse> updateMyInfo(@AuthenticationPrincipal Long userId,
                                                   @RequestBody UserUpdateRequest request) {
    return ResponseEntity.ok(userService.updateUserInfo(userId, request));
  }

  @Override
  @DeleteMapping
  public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal Long userId) {
    userService.deleteUser(userId);
    return ResponseEntity.noContent().build();
  }
}
