package org.ict.final_project_back_end.domain.user.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.user.dto.AvailabilityResponse;
import org.ict.final_project_back_end.domain.user.dto.LoginRequest;
import org.ict.final_project_back_end.domain.user.dto.SignupRequest;
import org.ict.final_project_back_end.domain.user.dto.TokenResponse;
import org.ict.final_project_back_end.domain.user.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// AuthController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
// 컨트롤러는 이 인터페이스를 implements 하고 @Override만 달아 실제 로직에 집중하고,
// API 설명/요청·응답 스키마 등 문서 관련 어노테이션은 모두 이곳에 모아둔다.
@Tag(name = "Auth", description = "이메일 기반 회원가입/로그인 및 로그인 상태 조회 API")
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public interface AuthApiDocs {

  @Operation(
      summary = "이메일 중복 확인",
      description = "회원가입 폼에서 이메일을 입력하는 즉시(제출 전) 사용 가능한 이메일인지 확인한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
  })
  ResponseEntity<AvailabilityResponse> checkEmail(
      @Parameter(description = "중복 여부를 확인할 이메일", required = true, example = "test@example.com")
      String email
  );

  @Operation(
      summary = "닉네임 중복 확인",
      description = "회원가입 폼에서 닉네임을 입력하는 즉시(제출 전) 사용 가능한 닉네임인지 확인한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
  })
  ResponseEntity<AvailabilityResponse> checkNickname(
      @Parameter(description = "중복 여부를 확인할 닉네임", required = true, example = "닉네임123")
      String nickname
  );

  @Operation(
      summary = "이메일 회원가입",
      description = "이메일/닉네임/비밀번호로 회원가입한다. 가입 직후 바로 로그인된 상태로 만들기 위해 JWT를 함께 발급해서 응답한다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "가입 성공, JWT 발급",
          content = @Content(schema = @Schema(implementation = TokenResponse.class))),
      @ApiResponse(responseCode = "400", description = "이미 사용 중인 이메일/닉네임이거나 요청 값이 올바르지 않음", content = @Content)
  })
  ResponseEntity<TokenResponse> signup(
      @RequestBody(description = "회원가입 요청 정보", required = true)
      SignupRequest request
  );

  @Operation(
      summary = "이메일 로그인",
      description = "이메일/비밀번호로 로그인하고 JWT를 발급받는다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "로그인 성공, JWT 발급",
          content = @Content(schema = @Schema(implementation = TokenResponse.class))),
      @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 일치하지 않음", content = @Content)
  })
  ResponseEntity<TokenResponse> login(
      @RequestBody(description = "로그인 요청 정보", required = true)
      LoginRequest request
  );

  @Operation(
      summary = "로그인 상태(내 정보) 조회",
      description = "JwtAuthFilter가 SecurityContext에 넣어둔 userId를 바탕으로 현재 로그인한 사용자 정보를 조회한다. "
          + "/api/** 는 인증이 필수이므로 유효한 토큰이 없으면 이 메서드에 도달하지 못하고 401이 반환된다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = UserResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청(토큰 없음/만료/위조)", content = @Content)
  })
  ResponseEntity<UserResponse> me(
      @Parameter(hidden = true)
      @AuthenticationPrincipal Long userId
  );
}
