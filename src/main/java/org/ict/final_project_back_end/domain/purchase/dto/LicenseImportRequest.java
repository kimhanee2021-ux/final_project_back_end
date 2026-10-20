package org.ict.final_project_back_end.domain.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 프론트가 스팀 라이선스 페이지(HTML)를 브라우저에서 파싱해 보내는 라이선스 목록
public record LicenseImportRequest(
    @NotNull @Size(max = 10000) List<@Valid License> licenses
) {

  public record License(
      @NotBlank @Size(max = 32) String externalKey,
      @NotNull LocalDate acquiredAt,
      @NotBlank @Size(max = 500) String name,
      @NotBlank @Pattern(regexp = "STORE|GIFT|COMPLIMENTARY|KEY|OTHER") String method,
      boolean freeLicense
  ) {
  }
}
