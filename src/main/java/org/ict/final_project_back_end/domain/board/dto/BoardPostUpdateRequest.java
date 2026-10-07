package org.ict.final_project_back_end.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record BoardPostUpdateRequest(
    @NotBlank(message = "카테고리를 선택해주세요.") @Size(max = 30) String category,
    Long appId,
    @Size(max = 50, message = "태그는 50자 이내로 입력해주세요.") String tag,
    @NotBlank(message = "제목을 입력해주세요.") @Size(max = 255, message = "제목은 255자 이내로 입력해주세요.") String title,
    @NotBlank(message = "내용을 입력해주세요.") String content,
    @NotNull(message = "익명 여부를 선택해주세요.") Boolean isAnonymous,
    LocalDateTime expiresAt) {
}
