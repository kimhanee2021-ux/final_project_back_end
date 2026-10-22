package org.ict.final_project_back_end.domain.analysis.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// AnalysisController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Analysis", description = "개인 소비 패턴 분석 API")
public interface AnalysisApiDocs {

  @Operation(
      summary = "내 소비 분석 데이터 조회",
      description = "DB에 저장된 내 스팀 보유 게임에 장르/썸네일과 한국 정가(원)를 붙여 반환한다. "
          + "금액은 실제 결제액이 아니라 현재 한국 정가 기준 추정치이며(amountSource=ESTIMATED), "
          + "무료 게임은 0원(FREE), 가격을 모르면 null(UNKNOWN)이다. "
          + "최신 플레이 시간이 필요하면 먼저 POST /api/library/steam/sync를 호출한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = SpendingAnalysisResponse.class))),
      @ApiResponse(responseCode = "400", description = "스팀 미연동 (code: STEAM_NOT_LINKED)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content)
  })
  ResponseEntity<SpendingAnalysisResponse> getMySpending(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );
}
