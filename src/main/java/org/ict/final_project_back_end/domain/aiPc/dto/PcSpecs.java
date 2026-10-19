package org.ict.final_project_back_end.domain.aiPc.dto;
import jakarta.validation.constraints.*;

// 기기명·제품ID·시리얼 등 개인 식별자는 수집/응답 DTO에 포함하지 않는다.
public record PcSpecs(
 @Size(max=160) String cpuModel,@Size(max=160) String cpuDetails,
 @Size(max=160) String gpuModel,@Size(max=160) String gpuMemory,
 @DecimalMin("0.1") @DecimalMax("4096") Double ramGB,@Size(max=80) String ramSpeed,
 @DecimalMin("0.1") @DecimalMax("100000") Double storageTotalGB,
 @DecimalMin("0.0") @DecimalMax("100000") Double storageFreeGB,@Size(max=160) String os
) {}
