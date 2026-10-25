package org.ict.final_project_back_end.domain.recommend.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.recommend.dto.RecommendationResponse;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// RecommendationController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Recommendation", description = "보유 게임 플레이 기록 기반 AI 게임 추천 API")
public interface RecommendationApiDocs {

  @Operation(
      summary = "내 AI 게임 추천 조회",
      description = "로그인한 사용자의 스팀 보유 게임 중 플레이 시간이 있는 게임으로 취향을 계산해 게임을 추천한다. "
          + "이미 보유한 게임은 추천하지 않는다. 계산은 FastAPI 추천 서버(Two-Tower 모델)가 하고, "
          + "이 API는 결과에 games 테이블의 이름·장르·썸네일을 붙인다. games에 없는 게임은 game이 null이다. "
          + "score는 순위용 값이며 확률이 아니다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "추천 성공",
          content = @Content(schema = @Schema(implementation = RecommendationResponse.class))),
      @ApiResponse(responseCode = "400", description = "limit/newSlots 범위 오류 (INVALID_REQUEST)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content),
      @ApiResponse(responseCode = "409", description = "추천에 쓸 수 있는 플레이한 게임이 없음 (NO_PLAYED_GAMES)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "502", description = "추천 서버 오류 (RECOMMEND_FAILED)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "503", description = "추천 서버에 연결할 수 없음 (RECOMMEND_UNAVAILABLE)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<RecommendationResponse> getMyRecommendations(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "추천 개수 (1~50)", example = "10") int limit,
      @Parameter(description = "limit 중 학습에 없던 신작으로 채울 칸 수 (0~50)", example = "3") int newSlots
  );
}
