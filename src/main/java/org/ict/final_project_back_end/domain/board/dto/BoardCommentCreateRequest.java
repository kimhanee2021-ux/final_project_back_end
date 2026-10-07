package org.ict.final_project_back_end.domain.board.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
public record BoardCommentCreateRequest(
    @NotBlank(message = "댓글을 입력해주세요.") @Size(max = 2000, message = "댓글은 2000자 이내로 입력해주세요.") String content,
    @Positive(message = "부모 댓글 ID는 양수여야 합니다.") Long parentId) { }
