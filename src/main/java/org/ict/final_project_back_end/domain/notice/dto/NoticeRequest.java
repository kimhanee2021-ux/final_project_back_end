package org.ict.final_project_back_end.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeRequest(
    @NotBlank(message = "제목을 입력해주세요.") @Size(max = 255, message = "제목은 255자 이내로 입력해주세요.") String title,
    @NotBlank(message = "내용을 입력해주세요.") @Size(max = 10000, message = "내용은 10000자 이내로 입력해주세요.") String content) { }
