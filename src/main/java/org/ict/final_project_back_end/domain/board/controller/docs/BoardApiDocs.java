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
import jakarta.validation.constraints.Size;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostPageResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// BoardController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Board", description = "게시글 작성/조회/수정/삭제 API")
public interface BoardApiDocs {

  @Operation(
      summary = "게시글 작성",
      description = "로그인한 사용자가 게시글을 작성한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "작성 성공",
          content = @Content(schema = @Schema(implementation = BoardPostCreateResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음(필수값 누락, 길이 초과 등)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 회원", content = @Content)
  })
  ResponseEntity<BoardPostCreateResponse> create(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @RequestBody(description = "게시글 작성 정보", required = true)
      @Valid BoardPostCreateRequest request
  );

  @Operation(
      summary = "게시글 단건 조회",
      description = "게시글 ID로 상세 내용을 조회한다. 작성자가 익명을 선택한 경우 authorId는 null로 내려간다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = BoardPostDetailResponse.class))),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<BoardPostDetailResponse> get(
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long id
  );

  @Operation(
      summary = "게시글 목록 조회",
      description = "카테고리/키워드로 필터링하고 최신순으로 페이징 조회한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = BoardPostPageResponse.class)))
  })
  ResponseEntity<BoardPostPageResponse> list(
      @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
      @Min(0) int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
      @Min(1) @Max(100) int size,
      @Parameter(description = "카테고리 필터(최대 30자)", example = "자유게시판")
      @Size(max = 30) String category,
      @Parameter(description = "제목/내용 검색 키워드(최대 200자)", example = "스팀세일")
      @Size(max = 200) String keyword
  );

  @Operation(
      summary = "게시글 수정",
      description = "작성자만 자신의 게시글을 수정할 수 있다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "수정 성공",
          content = @Content(schema = @Schema(implementation = BoardPostDetailResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음(필수값 누락, 길이 초과 등)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 수정할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<BoardPostDetailResponse> update(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long id,
      @RequestBody(description = "수정할 게시글 정보", required = true)
      @Valid BoardPostUpdateRequest request
  );

  @Operation(
      summary = "게시글 삭제",
      description = "작성자만 자신의 게시글을 삭제할 수 있다. 댓글 또는 이미지가 연결된 게시글은 삭제할 수 없다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 삭제할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content),
      @ApiResponse(responseCode = "409", description = "댓글 또는 이미지가 연결되어 삭제할 수 없음", content = @Content)
  })
  ResponseEntity<Void> delete(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long id
  );
}
