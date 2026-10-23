package org.ict.final_project_back_end.domain.board.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.ict.final_project_back_end.domain.board.dto.BoardCommentCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardCommentPageResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardCommentRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardCommentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// BoardCommentController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Board Comment", description = "게시글 댓글/답글 작성/조회/수정/삭제 API")
public interface BoardCommentApiDocs {

  @Operation(
      summary = "댓글/답글 작성",
      description = "parentId를 지정하면 답글로 등록된다. 답글에는 다시 답글을 달 수 없다(최대 2단계 깊이)."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "작성 성공",
          content = @Content(schema = @Schema(implementation = BoardCommentResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않거나, 답글에 다시 답글을 달려는 경우", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글 또는 부모 댓글", content = @Content)
  })
  ResponseEntity<BoardCommentResponse> create(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @RequestBody(description = "댓글 작성 정보(parentId를 지정하면 답글)", required = true)
      @Valid BoardCommentCreateRequest request
  );

  @Operation(
      summary = "댓글 목록 조회",
      description = "게시글에 달린 댓글/답글을 작성일 오름차순으로 페이징 조회한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = BoardCommentPageResponse.class))),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<BoardCommentPageResponse> list(
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
      @Min(0) int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
      @Min(1) @Max(100) int size
  );

  @Operation(
      summary = "댓글 수정",
      description = "작성자만 자신의 댓글/답글을 수정할 수 있다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "수정 성공",
          content = @Content(schema = @Schema(implementation = BoardCommentResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 수정할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글 또는 댓글", content = @Content)
  })
  ResponseEntity<BoardCommentResponse> update(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "댓글 ID", required = true, example = "10")
      Long commentId,
      @RequestBody(description = "수정할 댓글 내용", required = true)
      @Valid BoardCommentRequest request
  );

  @Operation(
      summary = "댓글 삭제",
      description = "작성자만 자신의 댓글/답글을 삭제할 수 있다. 답글이 달려 있는 댓글은 삭제할 수 없다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 삭제할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글 또는 댓글", content = @Content),
      @ApiResponse(responseCode = "409", description = "답글이 있는 댓글이라 삭제할 수 없음", content = @Content)
  })
  ResponseEntity<Void> delete(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "댓글 ID", required = true, example = "10")
      Long commentId
  );
}
