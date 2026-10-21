package org.ict.final_project_back_end.domain.aiCoach.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightOverview;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.service.SpendingAnalysisService;
import org.ict.final_project_back_end.domain.budget.service.BudgetService;
import org.ict.final_project_back_end.global.exception.AiCoachException;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CoachInsightServiceImpl implements CoachInsightService {

  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  // 결과 카드 아래 근거 칩은 많아야 4개
  static final int MAX_EVIDENCE = 4;
  // 숫자 확인에 실패한 결과는 한 번만 다시 만든다 (Gemini 호출 비용·대기 시간)
  private static final int MAX_ATTEMPTS = 2;

  private final SpendingAnalysisService spendingAnalysisService;
  private final BudgetService budgetService;
  private final CoachInsightAiClient aiClient;
  private final CoachInsightCache cache;
  private final CoachRateLimit rateLimit;

  public CoachInsightServiceImpl(SpendingAnalysisService spendingAnalysisService, BudgetService budgetService,
                                 CoachInsightAiClient aiClient, CoachInsightCache cache, CoachRateLimit rateLimit) {
    this.spendingAnalysisService = spendingAnalysisService;
    this.budgetService = budgetService;
    this.aiClient = aiClient;
    this.cache = cache;
    this.rateLimit = rateLimit;
  }

  @Override
  public CoachInsightOverview overview(Long userId) {
    SpendingAnalysisResponse spending = loadSpending(userId);
    Map<String, Object> facts = buildFacts(userId, spending);
    List<CoachInsightOverview.Topic> topics = InsightTopic.availableTopics(spending.hasPurchaseHistory()).stream()
        .map(topic -> new CoachInsightOverview.Topic(topic.key(), topic.label()))
        .toList();
    return new CoachInsightOverview(spending.hasPurchaseHistory(), profileOf(facts), topics);
  }

  @Override
  public CoachInsightResponse insight(Long userId, String topicKey) {
    InsightTopic topic = InsightTopic.from(topicKey);
    SpendingAnalysisResponse spending = loadSpending(userId);
    if (!topic.availableFor(spending.hasPurchaseHistory())) {
      throw new InvalidRequestException("구매내역을 가져온 뒤에 쓸 수 있는 분석입니다");
    }
    Map<String, Object> facts = buildFacts(userId, spending);
    // facts가 그대로면(같은 날, 같은 데이터) 저장해 둔 결과를 다시 보여준다
    int fingerprint = facts.hashCode();
    return cache.find(userId, topic, fingerprint).orElseGet(() -> {
      rateLimit.begin(userId);
      try {
        CoachInsightResponse insight = generate(facts, topic, spending);
        cache.put(userId, topic, fingerprint, insight);
        return insight;
      } finally {
        rateLimit.end(userId);
      }
    });
  }

  private SpendingAnalysisResponse loadSpending(Long userId) {
    if (userId == null) {
      throw new AuthenticationRequiredException();
    }
    // 스팀 미연동이면 여기서 SteamNotLinkedException(400)이 그대로 나간다
    SpendingAnalysisResponse spending = spendingAnalysisService.getMySpending(userId);
    if (spending.games().isEmpty()) {
      throw new InvalidRequestException("분석할 보유 게임이 없습니다");
    }
    return spending;
  }

  private Map<String, Object> buildFacts(Long userId, SpendingAnalysisResponse spending) {
    return CoachFactsBuilder.build(spending, budgetService.getCurrent(userId), LocalDate.now(KOREA));
  }

  // AI 문장의 숫자 자리표시자를 실제 값으로 바꾼다. 없는 칸을 가리키거나 숫자를 직접 쓰거나 숫자 근거가 하나도 없으면 한 번 더 만들고, 그래도 틀리면 버린다
  private CoachInsightResponse generate(Map<String, Object> facts, InsightTopic topic, SpendingAnalysisResponse spending) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      CoachInsightAiClient.Insight raw = aiClient.insight(facts, topic);
      List<String> problems = new ArrayList<>();
      List<CoachChatResponse.Evidence> evidence = new ArrayList<>();
      String verdict = render(raw.verdict(), facts, problems, evidence);
      String reason = render(raw.reason(), facts, problems, evidence);
      String comparison = render(raw.comparison(), facts, problems, evidence);
      String action = render(raw.action(), facts, problems, evidence);
      List<CoachAiClient.Recommendation> recommendations = new ArrayList<>();
      for (CoachAiClient.Recommendation recommendation : raw.recommendations()) {
        String why = render(recommendation.reason(), facts, problems, evidence);
        recommendations.add(new CoachAiClient.Recommendation(recommendation.appId(), why));
      }
      if (problems.isEmpty() && !evidence.isEmpty()) {
        // 추천 게임이 실제 보유 게임이 아니면 결과 전체를 버린다 (코치와 같은 규칙)
        return new CoachInsightResponse(topic.key(), verdict, reason, comparison, action,
            CoachServiceImpl.toCards(recommendations, spending.games()), firstEvidence(evidence));
      }
      log.warn("AI 분석 숫자 확인 실패 {} (시도 {}/{})", problems, attempt, MAX_ATTEMPTS);
    }
    throw AiCoachException.invalidResponse();
  }

  // 문장 하나의 자리표시자를 값으로 바꾸고, 문제와 쓰인 근거를 모은다
  private static String render(String text, Map<String, Object> facts, List<String> problems,
                               List<CoachChatResponse.Evidence> evidence) {
    CoachAnswerRenderer.Rendered rendered = CoachAnswerRenderer.render(text, facts, "");
    problems.addAll(rendered.problems());
    evidence.addAll(rendered.evidence());
    return rendered.text();
  }

  // facts.patterns.profile(CoachFactsBuilder가 규칙으로 정한 소비 유형)을 화면용으로 꺼낸다
  static CoachInsightOverview.Profile profileOf(Map<String, Object> facts) {
    Map<?, ?> patterns = (Map<?, ?>) facts.get("patterns");
    Map<?, ?> profile = (Map<?, ?>) patterns.get("profile");
    return new CoachInsightOverview.Profile(String.valueOf(profile.get("name")), String.valueOf(profile.get("meaning")));
  }

  // 같은 이름의 근거는 하나만, 앞에서부터 MAX_EVIDENCE개
  static List<CoachChatResponse.Evidence> firstEvidence(List<CoachChatResponse.Evidence> evidence) {
    Map<String, CoachChatResponse.Evidence> byLabel = new LinkedHashMap<>();
    evidence.forEach(e -> byLabel.putIfAbsent(e.label(), e));
    return new ArrayList<>(byLabel.values()).subList(0, Math.min(MAX_EVIDENCE, byLabel.size()));
  }
}
