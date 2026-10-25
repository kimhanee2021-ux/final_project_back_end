package org.ict.final_project_back_end.domain.search.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.ict.final_project_back_end.domain.search.dto.NaturalSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// SearchController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Search", description = "자연어 게임 검색 API")
public interface SearchApiDocs {

  @Operation(
      summary = "자연어로 게임 검색",
      description = "한국어 검색어(예: \"친구랑 같이 하는 무섭지 않은 협동 서바이벌\")를 AI가 영어 키워드(태그, 제외 태그, 설명, 비슷한 게임)로 바꾸고, "
          + "검색 모델이 의미가 비슷한 게임을 순위 순으로 돌려준다. analysis에 AI가 이해한 내용이 담기며, "
          + "analysis.analyzed가 false면 AI 분석이 실패해 검색어를 그대로 사용한 것이라 정확도가 떨어질 수 있다. "
          + "score는 요청 안에서 순위를 매기는 값이며 확률이나 일치율이 아니다. game은 games 카탈로그에 없는 게임이면 null이다. "
          + "AI 비용 보호를 위해 사용자당 분당 10회까지 가능하다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "검색 성공, 순위 순 결과 반환 (결과가 없으면 빈 목록)",
          content = @Content(schema = @Schema(implementation = NaturalSearchResponse.class))),
      @ApiResponse(responseCode = "400", description = "검색어가 비었거나 200자를 넘음, limit이 1~50 범위를 벗어남", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content),
      @ApiResponse(responseCode = "429", description = "검색 횟수 제한 초과 (SEARCH_RATE_LIMITED)", content = @Content),
      @ApiResponse(responseCode = "502", description = "검색 모델 서버 호출 실패 (SEARCH_SERVER_ERROR)", content = @Content)
  })
  ResponseEntity<NaturalSearchResponse> searchNatural(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "한국어 검색어 (1~200자)")
      @NotBlank @Size(max = 200) String query,
      @Parameter(description = "결과 개수 (1~50, 기본 10)")
      @Min(1) @Max(50) int limit
  );
}
