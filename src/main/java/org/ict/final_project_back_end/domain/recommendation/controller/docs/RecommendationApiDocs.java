package org.ict.final_project_back_end.domain.recommendation.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.recommendation.dto.RecommendationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// RecommendationController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Recommendation", description = "보유 게임 기반 게임 추천 API")
public interface RecommendationApiDocs {

  @Operation(
      summary = "내 보유 게임 기반 추천 조회",
      description = "플레이한 보유 게임으로 Two-Tower 모델이 게임을 추천한다. 마지막 추천을 저장해 두고, "
          + "저장본이 유효하면(TTL 안이고 요청 개수 이상) 그대로 돌려준다. 스팀 동기화·연동 해제 때 저장본은 지워진다. "
          + "score는 요청 안에서 순위를 매기는 값이며 확률이나 일치율이 아니다. "
          + "game은 games 카탈로그에 없는 게임이면 null이다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공, 순위 순 추천 목록 반환",
          content = @Content(schema = @Schema(implementation = RecommendationResponse.class))),
      @ApiResponse(responseCode = "400", description = "limit이 1~50 범위를 벗어남", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content),
      @ApiResponse(responseCode = "409", description = "추천에 쓸 플레이한 게임이 없음 (NO_PLAYED_GAMES)", content = @Content),
      @ApiResponse(responseCode = "502", description = "추천 모델 서버 호출 실패 (RECOMMEND_SERVER_ERROR)", content = @Content)
  })
  ResponseEntity<RecommendationResponse> getRecommendations(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "추천 개수 (1~50, 기본 10)")
      int limit
  );
}
