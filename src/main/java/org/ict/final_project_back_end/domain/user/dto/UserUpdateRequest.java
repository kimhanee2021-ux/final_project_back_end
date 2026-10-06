package org.ict.final_project_back_end.domain.user.dto;

// 내 정보 수정 요청. null인 필드는 "변경하지 않음"을 뜻한다.
// 비밀번호를 바꾸려면 현재 비밀번호(currentPassword)를 함께 보내야 한다.
public record UserUpdateRequest(String nickname, String currentPassword, String newPassword) {
}
