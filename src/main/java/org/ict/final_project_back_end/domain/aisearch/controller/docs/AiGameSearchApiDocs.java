package org.ict.final_project_back_end.domain.aisearch.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.aisearch.dto.AiGameSearchResponse;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;

// AiGameSearchController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "AI Search", description = "문장으로 설명한 게임을 찾는 AI 게임 검색 API")
public interface AiGameSearchApiDocs {

  @Operation(
      summary = "AI 게임 검색",
      description = "사용자가 한국어/영어로 적거나 말한 문장을 해석해 게임을 찾는다 (음성 요청도 글자로 바꿔 같은 API를 쓴다). "
          + "GPT가 요청 종류(intent: SEARCH 조건 검색, FIND_GAME 특정 게임, LATEST_NEWS 최신 소식 듣기, NEXT_NEWS/PAUSE/RESUME 브리핑 조작)와 "
          + "말한 게임의 영어 공식 제목, 영어 스팀 태그·설명·비슷한 게임을 뽑는다. 말한 게임은 games에서 이름으로 찾아 matchedGame으로 주고, "
          + "FastAPI 검색 모델(multilingual-e5 임베딩, 리뷰 100개 이상 게임 약 2만 2천 개)이 찾은 비슷한 게임은 items로 준다. "
          + "브리핑 조작 요청은 검색하지 않아 items가 비어 있다. score는 순위용 값이며 확률이 아니다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "검색 성공",
          content = @Content(schema = @Schema(implementation = AiGameSearchResponse.class))),
      @ApiResponse(responseCode = "400", description = "검색 문장 길이(2~200자)나 limit(1~30) 오류 (INVALID_REQUEST)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content),
      @ApiResponse(responseCode = "422", description = "게임을 찾는 문장으로 이해하지 못함 (AI_SEARCH_NOT_UNDERSTOOD)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "429", description = "GPT 요청 한도 초과 (AI_SEARCH_LIMITED)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "503", description = "GPT 키 미설정(AI_SEARCH_NOT_CONFIGURED) 또는 GPT·검색 모델 서버 장애(AI_SEARCH_UNAVAILABLE)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<AiGameSearchResponse> search(
      @Parameter(description = "찾고 싶은 게임 설명 (2~200자)", example = "친구랑 같이 하는 무서운 협동 게임") String query,
      @Parameter(description = "결과 개수 (1~30)", example = "12") int limit
  );
}
