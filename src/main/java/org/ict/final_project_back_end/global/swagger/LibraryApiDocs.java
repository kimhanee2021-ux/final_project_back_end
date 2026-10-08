package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.library.dto.LibraryGamePriceResponse;
import org.ict.final_project_back_end.domain.library.dto.LibraryGameResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

// LibraryController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Library", description = "스팀 보유 게임 라이브러리 동기화 API")
public interface LibraryApiDocs {

  @Operation(
      summary = "스팀 보유 게임 동기화",
      description = "연동된 스팀 계정의 보유 게임 목록을 스팀 Web API로 한 번에 조회해 DB에 저장(없으면 추가, 있으면 갱신)하고, "
          + "저장된 전체 게임 목록을 반환한다. 페이징은 하지 않는다. 스팀 계정 연동과 게임 세부 정보 공개 설정이 필요하다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "동기화 성공, 전체 게임 목록 반환",
          content = @Content(array = @ArraySchema(schema = @Schema(implementation = LibraryGameResponse.class)))),
      @ApiResponse(responseCode = "400", description = "스팀 미연동 또는 게임 목록이 비공개인 스팀 계정", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content),
      @ApiResponse(responseCode = "502", description = "스팀 Web API 호출 실패", content = @Content)
  })
  ResponseEntity<List<LibraryGameResponse>> syncSteamLibrary(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );

  @Operation(
      summary = "라이브러리 게임 가격 조회",
      description = "로그인한 사용자가 보유한 라이브러리 게임들의 원화(KRW) 가격을 조회한다. "
          + "games 테이블의 가격은 달러 기준 원본 텍스트라 사용하지 않고, 스팀 스토어 API(appdetails, cc=kr)에서 "
          + "받아온 값을 별도 테이블에 1일 주기로 캐싱해서 내려준다. 가격 조회에 실패한 게임은 가격 필드가 null로 내려온다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공, 라이브러리 게임별 가격 목록 반환",
          content = @Content(array = @ArraySchema(schema = @Schema(implementation = LibraryGamePriceResponse.class)))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content)
  })
  ResponseEntity<List<LibraryGamePriceResponse>> getLibraryGamePrices(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );
}
