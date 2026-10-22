package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.ict.final_project_back_end.domain.user.dto.UserUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// UserController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
// bearerAuth 보안 스킴은 AuthApiDocs에 정의되어 있으므로 여기서는 참조만 한다.
@Tag(name = "User", description = "로그인한 사용자 본인의 정보 조회/수정/삭제 API")
public interface UserApiDocs {

  @Operation(
      summary = "내 정보 조회",
      description = "JWT의 userId로 현재 로그인한 사용자의 정보를 조회한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = UserResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 회원", content = @Content)
  })
  ResponseEntity<UserResponse> getMyInfo(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );

  @Operation(
      summary = "내 정보 수정",
      description = "닉네임 및 비밀번호를 수정한다. null인 필드는 변경하지 않는다. "
          + "비밀번호를 변경하려면 currentPassword(현재 비밀번호)가 필요하다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "수정 성공",
          content = @Content(schema = @Schema(implementation = UserResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음(빈 값, 현재 비밀번호 누락)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않았거나 현재 비밀번호가 일치하지 않음", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 회원", content = @Content),
      @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임", content = @Content)
  })
  ResponseEntity<UserResponse> updateMyInfo(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId,
      @RequestBody(description = "수정할 정보", required = true)
      UserUpdateRequest request
  );

  @Operation(
      summary = "회원 탈퇴",
      description = "현재 로그인한 사용자와 그 사용자의 보유 게임 목록을 모두 삭제한다. 되돌릴 수 없다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "탈퇴 성공", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 회원", content = @Content)
  })
  ResponseEntity<Void> deleteMyAccount(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );

  @Operation(
      summary = "스팀 연동 해제",
      description = "현재 사용자의 스팀 연동을 끊고, 그 스팀 계정에서 동기화한 보유 게임 목록을 지운다. "
          + "직접 가져온 구매내역은 그대로 둔다(지우려면 DELETE /api/purchases). 연동하지 않은 상태에서 불러도 성공으로 본다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "해제 성공", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 회원", content = @Content)
  })
  ResponseEntity<Void> unlinkSteam(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );
}
