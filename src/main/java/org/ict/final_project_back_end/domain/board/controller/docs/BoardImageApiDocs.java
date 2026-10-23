package org.ict.final_project_back_end.domain.board.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.board.dto.BoardImageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// BoardImageController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Board Image", description = "게시글 이미지 업로드/조회/대표이미지 지정/삭제 API")
public interface BoardImageApiDocs {

  @Operation(
      summary = "게시글 이미지 업로드",
      description = "작성자만 자신의 게시글에 이미지를 업로드할 수 있다. JPG/PNG, 20MB 이하, 4천만 화소 이하 파일만 허용하며 "
          + "글당 최대 10장까지 업로드할 수 있다. 해당 게시글의 첫 업로드 이미지는 자동으로 대표이미지로 지정된다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "업로드 성공",
          content = @Content(schema = @Schema(implementation = BoardImageResponse.class))),
      @ApiResponse(responseCode = "400", description = "지원하지 않는 형식/용량 초과/손상된 이미지", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 업로드할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content),
      @ApiResponse(responseCode = "409", description = "게시글당 이미지 개수(10장)를 초과함", content = @Content)
  })
  ResponseEntity<BoardImageResponse> upload(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "업로드할 이미지 파일(JPG/PNG, 20MB 이하)", required = true)
      MultipartFile file
  );

  @Operation(
      summary = "게시글 이미지 목록 조회",
      description = "게시글에 업로드된 이미지를 정렬 순서대로 조회한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(array = @ArraySchema(schema = @Schema(implementation = BoardImageResponse.class)))),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글", content = @Content)
  })
  ResponseEntity<List<BoardImageResponse>> list(
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId
  );

  @Operation(
      summary = "게시글 이미지 파일 조회",
      description = "업로드된 이미지의 실제 바이너리 데이터를 반환한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary"))),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 이미지", content = @Content)
  })
  ResponseEntity<byte[]> file(
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "이미지 저장 키(파일명)", required = true, example = "a1b2c3.jpg")
      String key
  );

  @Operation(
      summary = "대표이미지 지정",
      description = "작성자만 게시글의 대표이미지를 지정할 수 있다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "지정 성공",
          content = @Content(schema = @Schema(implementation = BoardImageResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 지정할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글 또는 이미지", content = @Content)
  })
  ResponseEntity<BoardImageResponse> selectCover(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "대표이미지로 지정할 이미지 ID", required = true, example = "5")
      Long imageId
  );

  @Operation(
      summary = "게시글 이미지 삭제",
      description = "작성자만 자신의 게시글 이미지를 삭제할 수 있다. 삭제한 이미지가 대표이미지였다면 남은 이미지 중 하나가 자동으로 대표이미지가 된다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "403", description = "작성자가 아니어서 삭제할 수 없음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글 또는 이미지", content = @Content)
  })
  ResponseEntity<Void> delete(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "게시글 ID", required = true, example = "1")
      Long postId,
      @Parameter(description = "삭제할 이미지 ID", required = true, example = "5")
      Long imageId
  );
}
