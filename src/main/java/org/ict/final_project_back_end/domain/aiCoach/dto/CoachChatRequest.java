package org.ict.final_project_back_end.domain.aiCoach.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// message: 이번 질문, history: 앞선 대화(오래된 것부터, 최근 10개까지). role은 user(사용자) / model(코치)
public record CoachChatRequest(
    @NotBlank @Size(max = 500) String message,
    @Size(max = 10) List<@Valid Turn> history
) {

  public record Turn(
      @NotBlank @Pattern(regexp = "user|model") String role,
      @NotBlank @Size(max = 2000) String text
  ) {
  }
}
