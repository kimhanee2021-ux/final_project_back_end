package org.ict.final_project_back_end.domain.aiWorldcup.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record WorldcupRequest(@NotBlank @Size(max=80) String sessionId,@Min(3) @Max(24) int rounds,@NotNull @Size(max=26) List<@NotBlank @Size(max=20) String> groups,@NotNull @Size(max=24) List<@Valid Choice> history) {
  public record Choice(@Positive long leftId,@Positive long rightId,@NotBlank @Pattern(regexp="left|right|both|neither|unknown") String verdict,@NotNull @Size(max=6) List<@NotBlank @Size(max=30) String> reasons,@NotNull @Size(max=200) String note) {}
}
