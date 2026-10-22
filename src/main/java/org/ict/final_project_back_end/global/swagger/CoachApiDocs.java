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
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightOverview;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightResponse;
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

  @Operation(
      summary = "AI 분석 화면 개요(소비 유형·고를 수 있는 주제)",
      description = "AI를 부르지 않고 서버 계산만으로 바로 돌려준다. profile은 규칙으로 정한 소비 유형(수집가형·몰입형·세일 사냥꾼형·균형형), "
          + "topics는 지금 고를 수 있는 분석 태그다. 구매내역을 가져오지 않았으면 구매내역이 있어야 계산되는 주제(spending·budget)는 빠지고 "
          + "hasPurchaseHistory가 false다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "개요 성공",
          content = @Content(schema = @Schema(implementation = CoachInsightOverview.class))),
      @ApiResponse(responseCode = "400", description = "보유 게임 없음(INVALID_REQUEST) 또는 스팀 미연동(STEAM_NOT_LINKED)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content)
  })
  ResponseEntity<CoachInsightOverview> insightOverview(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

  @Operation(
      summary = "AI 분석 결과 카드(주제별)",
      description = "내 소비 분석 데이터를 서버가 미리 계산해 Gemini에 보내고, 고른 주제(topic: spending 소비 패턴, library 라이브러리, "
          + "value 가성비, activity 최근 활동, budget 예산·환불)에 맞는 판단·이유·비교·다음 행동을 받는다. 문장 속 숫자는 서버가 계산한 값만 쓰며 "
          + "쓰인 숫자의 근거를 evidence로 함께 준다(최대 4개). 다음 행동에서 권한 게임은 보유 게임 중에서만 최대 3개(games)다. "
          + "같은 날 같은 데이터면 서버 메모리에 저장해 둔 결과를 그대로 돌려준다. 분당 호출 제한은 코치 질문과 같다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "분석 성공",
          content = @Content(schema = @Schema(implementation = CoachInsightResponse.class))),
      @ApiResponse(responseCode = "400", description = "topic 값 오류·구매내역이 필요한 주제(INVALID_REQUEST), 보유 게임 없음, 또는 스팀 미연동(STEAM_NOT_LINKED)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content),
      @ApiResponse(responseCode = "429", description = "요청이 너무 많음 (AI_RATE_LIMITED)", content = @Content),
      @ApiResponse(responseCode = "502", description = "AI 결과 형식·숫자 확인 실패 또는 보유하지 않은 게임 추천 (AI_INVALID_RESPONSE)", content = @Content),
      @ApiResponse(responseCode = "503", description = "Gemini 키·모델 설정 없음(AI_NOT_CONFIGURED) 또는 Gemini 혼잡(AI_BUSY)", content = @Content),
      @ApiResponse(responseCode = "504", description = "AI 응답 시간 초과 (AI_TIMEOUT)", content = @Content)
  })
  ResponseEntity<CoachInsightResponse> insight(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
                                               @Parameter(description = "spending, library, value, activity, budget 중 하나") String topic);
}
