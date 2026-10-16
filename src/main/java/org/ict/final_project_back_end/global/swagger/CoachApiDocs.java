package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// CoachController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "AI Coach", description = "AI 게임 소비 코치 API")
public interface CoachApiDocs {

  @Operation(
      summary = "AI 소비 코치에게 질문",
      description = "내 소비 분석 데이터(실제 결제액·플레이 시간·할인·미플레이·이번 달 예산)를 서버가 미리 계산해 Gemini에 함께 보내고, "
          + "질문에 맞는 조언을 받는다. 금액·시간 같은 숫자는 서버가 계산한 값만 쓰도록 하며, 게임 추천은 보유 게임 중에서만 최대 3개다 "
          + "(보유하지 않은 게임이 섞이면 답을 버리고 502). 구매내역 원본과 결제 정보는 AI에 보내지 않는다. "
          + "history는 앞선 대화(최근 10개까지, role: user/model)이며 서버는 최근 6개만 사용한다. 회원당 분당 8회, 동시에 1개까지."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "답변 성공",
          content = @Content(schema = @Schema(implementation = CoachChatResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 형식 오류(VALIDATION_ERROR) 또는 스팀 미연동(STEAM_NOT_LINKED)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "429", description = "질문이 너무 많음 (AI_RATE_LIMITED)", content = @Content),
      @ApiResponse(responseCode = "502", description = "AI 답변 형식 오류·보유하지 않은 게임 추천 (AI_INVALID_RESPONSE)", content = @Content),
      @ApiResponse(responseCode = "503", description = "Gemini 키·모델 설정 없음(AI_NOT_CONFIGURED) 또는 두 번 다시 시도해도 Gemini 혼잡(AI_BUSY)", content = @Content),
      @ApiResponse(responseCode = "504", description = "AI 응답 시간 초과 (AI_TIMEOUT)", content = @Content)
  })
  ResponseEntity<CoachChatResponse> chat(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
                                         CoachChatRequest request);
}
