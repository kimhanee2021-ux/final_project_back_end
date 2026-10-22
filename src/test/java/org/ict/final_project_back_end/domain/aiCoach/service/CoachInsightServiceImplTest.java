package org.ict.final_project_back_end.domain.aiCoach.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightOverview;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.budget.dto.BudgetRequest;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;
import org.ict.final_project_back_end.domain.budget.service.BudgetService;
import org.ict.final_project_back_end.global.exception.AiCoachException;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// AI 분석 화면: 태그(주제)가 구매내역 유무에 따라 보이는지, 숫자는 서버 값으로 채우는지, 틀린 결과는 한 번만 다시 만드는지,
// 같은 데이터면 저장해 둔 결과를 쓰는지 검증한다 (Gemini 호출 없이)
class CoachInsightServiceImplTest {

  private static SpendingGameResponse game(long appId, String name, int amount, double hours) {
    return new SpendingGameResponse(appId, name, "Action", List.of("Action"), null, amount, "CONFIRMED",
        null, null, hours, 0, null, null, 0, LocalDate.of(2026, 3, 1), 0, 0, false, null, null);
  }

  private static SpendingAnalysisResponse spending(List<SpendingGameResponse> games, boolean hasHistory) {
    return new SpendingAnalysisResponse(LocalDate.of(2026, 10, 16), hasHistory ? "PURCHASE_HISTORY" : "ESTIMATE", games, hasHistory,
        0, 0, 0, 0, List.of());
  }

  private final BudgetService budget = new BudgetService() {
    @Override
    public BudgetResponse getCurrent(Long userId) {
      return new BudgetResponse("2026-10", null);
    }

    @Override
    public BudgetResponse setCurrent(Long userId, BudgetRequest request) {
      throw new UnsupportedOperationException();
    }
  };

  private static CoachInsightAiClient.Insight insight(String verdict, String reason, String comparison, String action) {
    return new CoachInsightAiClient.Insight(verdict, reason, comparison, action, List.of());
  }

  // 미리 정해 둔 결과를 차례로 돌려주는 가짜 AI. 어떤 주제로 몇 번 불렀는지도 센다
  private static final class FakeAi implements CoachInsightAiClient {
    final List<Insight> insights;
    final List<InsightTopic> topics = new ArrayList<>();

    FakeAi(Insight... insights) {
      this.insights = List.of(insights);
    }

    @Override
    public Insight insight(Map<String, Object> facts, InsightTopic topic) {
      topics.add(topic);
      return insights.get(Math.min(topics.size(), insights.size()) - 1);
    }
  }

  private CoachInsightServiceImpl service(FakeAi ai, List<SpendingGameResponse> games, boolean hasHistory, CoachInsightCache cache) {
    return new CoachInsightServiceImpl(userId -> spending(games, hasHistory), budget, ai, cache, new CoachRateLimit());
  }

  private final List<SpendingGameResponse> library = List.of(game(1, "Dead Cells", 20000, 40), game(2, "Ori 2", 5000, 0));
  private static final CoachInsightAiClient.Insight GOOD =
      insight("지금까지 {{totals.totalSpentKrw}}을 썼어요.", "유료 게임이 {{totals.paidGames}}개예요.", "", "Ori 2부터 해 보세요.");

  @Test
  void showsAllTopicsWithPurchaseHistoryAndHidesHistoryOnlyOnesWithout() {
    CoachInsightOverview with = service(new FakeAi(GOOD), library, true, new CoachInsightCache()).overview(1L);
    assertTrue(with.hasPurchaseHistory());
    assertEquals(List.of("spending", "library", "value", "activity", "budget"),
        with.topics().stream().map(CoachInsightOverview.Topic::key).toList());

    CoachInsightOverview without = service(new FakeAi(GOOD), library, false, new CoachInsightCache()).overview(1L);
    assertFalse(without.hasPurchaseHistory());
    assertEquals(List.of("library", "value", "activity"), without.topics().stream().map(CoachInsightOverview.Topic::key).toList());
  }

  @Test
  void overviewAttachesTheRuleBasedProfileWithoutCallingAi() {
    FakeAi ai = new FakeAi(GOOD);
    // Ori 2는 0시간이라 유료 게임 2개 중 1개(50%)가 미플레이 -> 수집가형
    CoachInsightOverview overview = service(ai, library, false, new CoachInsightCache()).overview(1L);
    assertEquals("수집가형", overview.profile().name());
    assertFalse(overview.profile().meaning().isBlank());
    assertTrue(ai.topics.isEmpty());
  }

  @Test
  void fillsNumbersFromFactsInEveryPartAndReturnsThemAsEvidence() {
    FakeAi ai = new FakeAi(insight("지금까지 {{totals.totalSpentKrw}}를 썼어요.", "유료 게임이 {{totals.paidGames}}개예요.",
        "총 플레이는 {{totals.totalPlayHours}}예요.", "Ori 2를 먼저 해 보세요."));
    CoachInsightResponse result = service(ai, library, false, new CoachInsightCache()).insight(1L, "library");
    assertEquals("library", result.topic());
    // AI가 쓴 "원를"의 조사를 받침에 맞게 "원을"로 고친다
    assertEquals("지금까지 25,000원을 썼어요.", result.verdict());
    assertEquals("유료 게임이 2개예요.", result.reason());
    assertEquals("총 플레이는 40시간이에요.", result.comparison());
    assertEquals("Ori 2를 먼저 해 보세요.", result.action());
    assertEquals(List.of("누적 지출", "유료 게임 수", "총 플레이 시간"),
        result.evidence().stream().map(CoachChatResponse.Evidence::label).toList());
    assertEquals(List.of(InsightTopic.LIBRARY), ai.topics);
  }

  @Test
  void allowsAnEmptyComparisonWhenThereIsNothingToCompare() {
    CoachInsightResponse result = service(new FakeAi(GOOD), library, false, new CoachInsightCache()).insight(1L, "value");
    assertEquals("", result.comparison());
  }

  @Test
  void turnsRecommendedOwnedGamesIntoCardsAndDropsAnswersWithGamesNotOwned() {
    CoachInsightAiClient.Insight owned = new CoachInsightAiClient.Insight("{{totals.paidGames}}개를 샀어요.", "이유예요.", "", "Ori 2를 해 보세요.",
        List.of(new CoachAiClient.Recommendation(2L, "아직 {{ownedGames.2.playHours}}예요.")));
    CoachInsightResponse result = service(new FakeAi(owned), library, false, new CoachInsightCache()).insight(1L, "library");
    assertEquals(1, result.games().size());
    assertEquals("Ori 2", result.games().get(0).name());

    CoachInsightAiClient.Insight stranger = new CoachInsightAiClient.Insight("{{totals.paidGames}}개를 샀어요.", "이유예요.", "", "해 보세요.",
        List.of(new CoachAiClient.Recommendation(999L, "없는 게임")));
    assertThrows(AiCoachException.class, () -> service(new FakeAi(stranger), library, false, new CoachInsightCache()).insight(1L, "library"));
  }

  @Test
  void refusesHistoryOnlyTopicsWithoutPurchaseHistoryAndNeverCallsAi() {
    FakeAi ai = new FakeAi(GOOD);
    CoachInsightServiceImpl noHistory = service(ai, library, false, new CoachInsightCache());
    assertThrows(InvalidRequestException.class, () -> noHistory.insight(1L, "spending"));
    assertThrows(InvalidRequestException.class, () -> noHistory.insight(1L, "budget"));
    assertTrue(ai.topics.isEmpty());
  }

  @Test
  void asksAgainOnceWhenAPartHasANumberItWroteByItself() {
    FakeAi ai = new FakeAi(insight("2만 5000원을 썼어요.", "이유예요.", "", "행동이에요."), GOOD);
    CoachInsightResponse result = service(ai, library, false, new CoachInsightCache()).insight(1L, "value");
    assertEquals("지금까지 25,000원을 썼어요.", result.verdict());
    assertEquals(2, ai.topics.size());
  }

  @Test
  void dropsTheResultWhenTheSecondTryIsStillWrongOrHasNoNumberEvidence() {
    FakeAi wrong = new FakeAi(insight("{{totals.noSuchField}}을 썼어요.", "이유예요.", "", "행동이에요."));
    assertThrows(AiCoachException.class, () -> service(wrong, library, false, new CoachInsightCache()).insight(1L, "value"));
    assertEquals(2, wrong.topics.size());

    FakeAi noEvidence = new FakeAi(insight("균형 잡힌 편이에요.", "이유예요.", "", "행동이에요."));
    assertThrows(AiCoachException.class, () -> service(noEvidence, library, false, new CoachInsightCache()).insight(1L, "value"));
  }

  @Test
  void reusesTheSavedResultWhileTheDataIsTheSameAndSeparatesTopics() {
    FakeAi ai = new FakeAi(GOOD, insight("다른 {{totals.paidGames}}개예요.", "이유예요.", "", "행동이에요."));
    CoachInsightCache cache = new CoachInsightCache();
    CoachInsightResponse first = service(ai, library, false, cache).insight(1L, "library");
    CoachInsightResponse second = service(ai, library, false, cache).insight(1L, "library");
    assertEquals(first, second);
    assertEquals(1, ai.topics.size());
    // 주제가 다르면 따로 만든다
    service(ai, library, false, cache).insight(1L, "value");
    assertEquals(2, ai.topics.size());
  }

  @Test
  void makesANewResultWhenTheDataChanges() {
    FakeAi ai = new FakeAi(insight("총 {{totals.paidGames}}개예요.", "이유예요.", "", "행동이에요."),
        insight("이제 {{totals.paidGames}}개예요.", "이유예요.", "", "행동이에요."));
    CoachInsightCache cache = new CoachInsightCache();
    service(ai, library, false, cache).insight(1L, "library");
    CoachInsightResponse changed = service(ai, List.of(game(1, "Dead Cells", 20000, 40)), false, cache).insight(1L, "library");
    assertEquals("이제 1개예요.", changed.verdict());
    assertEquals(2, ai.topics.size());
  }

  @Test
  void rejectsMissingLoginUnknownTopicAndEmptyLibrary() {
    FakeAi ai = new FakeAi(GOOD);
    assertThrows(AuthenticationRequiredException.class, () -> service(ai, library, false, new CoachInsightCache()).insight(null, "library"));
    assertThrows(AuthenticationRequiredException.class, () -> service(ai, library, false, new CoachInsightCache()).overview(null));
    assertThrows(InvalidRequestException.class, () -> service(ai, library, false, new CoachInsightCache()).insight(1L, "home"));
    assertThrows(InvalidRequestException.class, () -> service(ai, List.of(), false, new CoachInsightCache()).insight(1L, "library"));
    assertTrue(ai.topics.isEmpty());
  }

  @Test
  void keepsAtMostFourEvidenceWithoutDuplicates() {
    List<CoachChatResponse.Evidence> evidence = List.of(
        new CoachChatResponse.Evidence("a", "1"), new CoachChatResponse.Evidence("a", "중복"),
        new CoachChatResponse.Evidence("b", "2"), new CoachChatResponse.Evidence("c", "3"),
        new CoachChatResponse.Evidence("d", "4"), new CoachChatResponse.Evidence("e", "5"));
    assertEquals(List.of("a", "b", "c", "d"),
        CoachInsightServiceImpl.firstEvidence(evidence).stream().map(CoachChatResponse.Evidence::label).toList());
  }

  @Test
  void readsAnInsightOnlyFromAFinishedWellFormedResponse() throws Exception {
    ObjectMapper json = new ObjectMapper();
    String ok = "{\"verdict\":\" 판단 \",\"reason\":\"이유\",\"comparison\":\"\",\"action\":\"행동\","
        + "\"recommendations\":[{\"appId\":\"7\",\"reason\":\"그냥\"}]}";
    CoachInsightAiClient.Insight parsed = GeminiInsightClient.parseInsight(json, geminiBody("STOP", ok));
    assertEquals("판단", parsed.verdict());
    assertEquals("", parsed.comparison());
    assertEquals(7L, parsed.recommendations().get(0).appId());

    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json, geminiBody("MAX_TOKENS", ok)));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json, geminiBody("STOP", "JSON이 아님")));
    // 판단·이유·행동이 비면 버린다
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[]}")));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"\",\"recommendations\":[]}")));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"" + "가".repeat(401) + "\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[]}")));
    // 추천이 네 개거나 appId가 숫자가 아니면 버린다
    String four = "[" + "{\"appId\":\"1\",\"reason\":\"r\"},".repeat(3) + "{\"appId\":\"1\",\"reason\":\"r\"}]";
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":" + four + "}")));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[{\"appId\":\"게임\",\"reason\":\"r\"}]}")));
  }

  @Test
  void topicKeysMapToTopics() {
    assertEquals(InsightTopic.SPENDING, InsightTopic.from("spending"));
    assertEquals(InsightTopic.BUDGET, InsightTopic.from("budget"));
    assertThrows(InvalidRequestException.class, () -> InsightTopic.from("SPENDING"));
    assertEquals(List.of(InsightTopic.LIBRARY, InsightTopic.VALUE, InsightTopic.ACTIVITY), InsightTopic.availableTopics(false));
  }

  private static byte[] geminiBody(String finishReason, String text) throws Exception {
    ObjectMapper json = new ObjectMapper();
    return json.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", finishReason,
        "content", Map.of("parts", List.of(Map.of("text", text)))))));
  }
}
