package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.budget.dto.BudgetRequest;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// BudgetController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Budget", description = "월 게임 지출 예산 API (AI 소비 코치가 참고)")
public interface BudgetApiDocs {

  @Operation(
      summary = "이번 달 예산 조회",
      description = "한국 시간 기준 이번 달(예: 2026-10)의 게임 지출 예산을 조회한다. 설정하지 않았으면 limitAmount가 null이다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = BudgetResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content)
  })
  ResponseEntity<BudgetResponse> getCurrent(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

  @Operation(
      summary = "이번 달 예산 설정",
      description = "이번 달 게임 지출 예산(0 ~ 10,000,000원)을 저장한다. 이미 있으면 금액만 바꾼다. "
          + "AI 소비 코치가 이번 달 지출과 비교해 남은 예산을 알려 줄 때 사용한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "저장 성공",
          content = @Content(schema = @Schema(implementation = BudgetResponse.class))),
      @ApiResponse(responseCode = "400", description = "금액 범위 오류 (code: VALIDATION_ERROR)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content)
  })
  ResponseEntity<BudgetResponse> setCurrent(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
                                            BudgetRequest request);
}
