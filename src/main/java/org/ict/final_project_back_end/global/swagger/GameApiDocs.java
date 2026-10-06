package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.game.dto.GameCursorPageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSyncStatusResponse;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// GameController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
// 게임 조회(GET /api/games/**)는 토큰 없이 접근할 수 있으므로 보안 요구사항을 표시하지 않는다.
@Tag(name = "Game", description = "저장된 스팀 게임 목록·검색·상세 조회 API")
public interface GameApiDocs {

  @Operation(
      summary = "게임 목록 조회 (페이징)",
      description = "DB에 저장된 게임을 appId 오름차순으로 페이지 단위 조회한다. size는 최대 100으로 제한된다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  ResponseEntity<GamePageResponse<GameResponse>> getGames(
      @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기 (1~100, 기본 20)", example = "20")
      @RequestParam(defaultValue = "20") int size
  );

  @Operation(
      summary = "게임 이름 검색 (페이징)",
      description = "게임 이름에 키워드가 포함된 게임을 대소문자 구분 없이 검색한다. 키워드 앞뒤 공백은 제거된다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "검색 성공"),
      @ApiResponse(responseCode = "400", description = "검색어 누락 또는 공백만 입력",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<GamePageResponse<GameResponse>> searchGames(
      @Parameter(description = "검색 키워드 (게임 이름에 포함되는지 검사)", example = "portal", required = true)
      @RequestParam String keyword,
      @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기 (1~100, 기본 20)", example = "20")
      @RequestParam(defaultValue = "20") int size
  );

  @Operation(
      summary = "전체 게임 커서 조회",
      description = "appId 오름차순 커서 방식으로 게임을 이어서 받는다. 첫 요청은 afterAppId를 생략하고, "
          + "이후에는 응답의 nextAfterAppId를 afterAppId로 넘긴다. hasNext가 false이면 마지막 페이지다. "
          + "size는 최대 500으로 제한된다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  ResponseEntity<GameCursorPageResponse> getAllGames(
      @Parameter(description = "이전 응답의 nextAfterAppId (처음이면 생략)", example = "730")
      @RequestParam(required = false) Long afterAppId,
      @Parameter(description = "한 번에 가져올 개수 (1~500, 기본 100)", example = "100")
      @RequestParam(defaultValue = "100") int size
  );

  @Operation(
      summary = "전체 게임 동기화 상태 조회",
      description = "전체 게임 동기화가 어디까지 진행되었는지 조회한다. 동기화가 한 번도 실행되지 않았으면 "
          + "lastAppId와 lastCompletedAt은 null이다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  ResponseEntity<GameSyncStatusResponse> getSyncStatus();

  @Operation(
      summary = "게임 상세 조회",
      description = "appId에 해당하는 게임의 상세정보를 조회한다. 상세정보는 1일 동안 DB에 캐시되며, 만료되었으면 "
          + "스팀 스토어에서 다시 가져온다. 스팀 호출이 실패해도 캐시된 데이터가 있으면 그것을 반환한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "저장되지 않은 게임이거나, 스팀 스토어에 상세정보가 없는 게임",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "502", description = "스팀 스토어 호출 실패 (캐시된 상세정보도 없는 경우)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<GameDetailResponse> getGameDetail(
      @Parameter(description = "스팀 앱 ID", example = "440", required = true)
      @PathVariable Long appId
  );
}
