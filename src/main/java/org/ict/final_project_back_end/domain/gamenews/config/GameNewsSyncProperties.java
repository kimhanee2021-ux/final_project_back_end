package org.ict.final_project_back_end.domain.gamenews.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Validated
@ConfigurationProperties("app.news.sync")
public record GameNewsSyncProperties(
    @DefaultValue({"570", "730", "440"}) @NotEmpty @Size(max = 20) List<@NotNull @Min(1) @Max(4294967295L) Long> appIds,
    @DefaultValue("5") @Min(1) @Max(100) int newsCount,
    @DefaultValue("5") @Min(1) @Max(100) int briefingBatchSize,
    @DefaultValue("1000") @Min(0) @Max(10000) long requestDelayMs) {
  public GameNewsSyncProperties { appIds = appIds == null ? List.of() : List.copyOf(appIds); }
}
