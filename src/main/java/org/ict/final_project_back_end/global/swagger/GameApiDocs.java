package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.springframework.http.ResponseEntity;

// GameController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Game", description = "게임 목록/검색 API")
public interface GameApiDocs {

  @Operation(
      summary = "게임 목록 조회",
      description = "장르로 필터링하고 appId(수집 순서) 내림차순으로 페이징 조회한다. "
          + "games 테이블에 출시일 데이터가 없어 현재는 appId 내림차순을 임시 기준으로 사용한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = GamePageResponse.class)))
  })
  ResponseEntity<GamePageResponse> list(
      @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
      @Min(0) int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
      @Min(1) @Max(100) int size,
      @Parameter(description = "장르 필터(부분 일치, 최대 50자)", example = "RPG")
      @Size(max = 50) String genre
  );

  @Operation(
      summary = "게임 제목 검색",
      description = "제목 키워드로 부분 일치 검색하고 appId(수집 순서) 내림차순으로 페이징 조회한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "검색 성공",
          content = @Content(schema = @Schema(implementation = GamePageResponse.class))),
      @ApiResponse(responseCode = "400", description = "검색어가 비어있음", content = @Content)
  })
  ResponseEntity<GamePageResponse> search(
      @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
      @Min(0) int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
      @Min(1) @Max(100) int size,
      @Parameter(description = "제목 검색 키워드(부분 일치, 최대 100자)", required = true, example = "Dragon")
      @NotBlank @Size(max = 100) String name
  );
}
