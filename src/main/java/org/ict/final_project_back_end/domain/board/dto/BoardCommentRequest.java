package org.ict.final_project_back_end.domain.board.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record BoardCommentRequest(
    @NotBlank(message = "댓글을 입력해주세요.") @Size(max = 2000, message = "댓글은 2000자 이내로 입력해주세요.") String content) { }
