package org.ict.final_project_back_end.domain.aiGame.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AiRequest(
    @NotNull @Pattern(regexp="recommend|purchase") String mode,
    @NotBlank @Size(max=2000) String message,
    @Size(max=60) String genre,
    @Positive Long gameId,
    @Size(max=6) List<@NotNull @Valid Turn> history) {
  public record Turn(@NotNull @Pattern(regexp="user|model") String role, @NotBlank @Size(max=2000) String text) { }
}
