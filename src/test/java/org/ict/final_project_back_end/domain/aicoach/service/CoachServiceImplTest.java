package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.aicoach.client.CoachAiClient;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// AI가 추천한 게임을 보유 게임으로만 받는지 검증한다 (없는 게임을 지어내면 답을 버림)
class CoachServiceImplTest {

  private static SpendingGameResponse owned(long appId, String name) {
    return new SpendingGameResponse(appId, name, "Action", List.of("Action"), "https://img/" + appId, 1000, "CONFIRMED",
        null, null, 0, 0, null, null, 0, null, 0, 0, false, null, null);
  }

  private final List<SpendingGameResponse> library = List.of(owned(10, "Dead Cells"), owned(20, "Ori 2"));

  @Test
  void turnsOwnedRecommendationsIntoCardsWithoutDuplicates() {
    List<CoachChatResponse.GameCard> cards = CoachServiceImpl.toCards(List.of(
        new CoachAiClient.Recommendation(20L, "아직 0시간"),
        new CoachAiClient.Recommendation(20L, "중복"),
        new CoachAiClient.Recommendation(10L, "가성비 1위")), library);
    assertEquals(List.of(
        new CoachChatResponse.GameCard(20L, "Ori 2", "https://img/20", "아직 0시간"),
        new CoachChatResponse.GameCard(10L, "Dead Cells", "https://img/10", "가성비 1위")), cards);
  }

  @Test
  void rejectsAnswersThatRecommendGamesTheUserDoesNotOwn() {
    assertThrows(AiCoachException.class, () -> CoachServiceImpl.toCards(
        List.of(new CoachAiClient.Recommendation(999L, "없는 게임")), library));
  }

  @Test
  void rateLimitAllowsOneQuestionAtATime() {
    CoachRateLimit limit = new CoachRateLimit();
    limit.begin(1L);
    assertThrows(AiCoachException.class, () -> limit.begin(1L));
    limit.end(1L);
    limit.begin(1L);
    limit.end(1L);
    // 다른 회원은 영향 없음
    limit.begin(2L);
    limit.end(2L);
  }
}
