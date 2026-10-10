package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.board.dto.BoardLikeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// BoardLikeController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Board Like", description = "게시글 좋아요 조회/등록/취소 API")
public interface BoardLikeApiDocs {

  @Operation(
      summary = "좋아요 상태 조회",
      description = "게시글의 좋아요 총개수와 현재 로그인한 사용자의 좋아요 여부를 조회한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = BoardLikeResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<BoardLikeResponse> status(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId
  );

  @Operation(
      summary = "좋아요 등록",
      description = "게시글에 좋아요를 등록한다. 이미 좋아요를 누른 상태라면 그대로 현재 상태를 반환한다(중복 등록되지 않음)."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "등록 성공",
          content = @Content(schema = @Schema(implementation = BoardLikeResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<BoardLikeResponse> like(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId
  );

  @Operation(
      summary = "좋아요 취소",
      description = "게시글에 등록한 좋아요를 취소한다. 좋아요를 누르지 않은 상태라면 그대로 현재 상태를 반환한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "취소 성공",
          content = @Content(schema = @Schema(implementation = BoardLikeResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<BoardLikeResponse> cancel(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId
  );
}
