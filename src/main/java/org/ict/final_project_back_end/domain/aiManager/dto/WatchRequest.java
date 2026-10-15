package org.ict.final_project_back_end.domain.aiManager.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public record WatchRequest(@NotNull @Size(min=1,max=6) List<@Positive Long> appIds,@Min(1) @Max(10000000) long targetWon,@NotBlank @Size(max=100) String label){}
