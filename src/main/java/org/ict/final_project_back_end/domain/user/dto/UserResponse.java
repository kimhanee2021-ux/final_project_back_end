package org.ict.final_project_back_end.domain.user.dto;

import org.ict.final_project_back_end.domain.user.entity.User;

public record UserResponse(Long id, String email, String nickname) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
  }
}
