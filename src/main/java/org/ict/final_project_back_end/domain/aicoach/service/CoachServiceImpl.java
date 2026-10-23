package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.aicoach.client.CoachAiClient;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.analysis.service.SpendingAnalysisService;
import org.ict.final_project_back_end.domain.budget.service.BudgetService;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CoachServiceImpl implements CoachService {

  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  // 앞선 대화는 최근 것만 보낸다 (토큰·비용 절약)
  private static final int MAX_HISTORY = 6;
  // 숫자 확인에 실패한 답은 한 번만 다시 묻는다 (Gemini 호출 비용·대기 시간)
  private static final int MAX_ATTEMPTS = 2;

  private final SpendingAnalysisService spendingAnalysisService;
  private final BudgetService budgetService;
  private final CoachAiClient aiClient;
  private final CoachRateLimit rateLimit;

  public CoachServiceImpl(SpendingAnalysisService spendingAnalysisService, BudgetService budgetService,
                          CoachAiClient aiClient, CoachRateLimit rateLimit) {
    this.spendingAnalysisService = spendingAnalysisService;
    this.budgetService = budgetService;
    this.aiClient = aiClient;
    this.rateLimit = rateLimit;
  }

  @Override
  public CoachChatResponse chat(Long userId, CoachChatRequest request) {
    if (userId == null) {
      throw new AuthenticationRequiredException();
    }
    rateLimit.begin(userId);
    try {
      // 스팀 미연동이면 여기서 SteamNotLinkedException(400)이 그대로 나간다
      SpendingAnalysisResponse spending = spendingAnalysisService.getMySpending(userId);
      Map<String, Object> facts = CoachFactsBuilder.build(spending, budgetService.getCurrent(userId), LocalDate.now(KOREA));
      Verified reply = replyWithVerifiedNumbers(facts, recentHistory(request.history()), request.message().trim());
      return new CoachChatResponse(reply.answer(), toCards(reply.recommendations(), spending.games()), reply.evidence());
    } finally {
      rateLimit.end(userId);
    }
  }

  // AI 답변의 숫자 자리표시자를 실제 값으로 바꾼다. 없는 칸을 가리키거나 숫자를 직접 쓴 답이면 한 번 더 묻고, 그래도 틀리면 답을 보여주지 않는다
  private Verified replyWithVerifiedNumbers(Map<String, Object> facts, List<CoachChatRequest.Turn> history, String message) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      CoachAiClient.Reply reply = aiClient.reply(facts, history, message);
      CoachAnswerRenderer.Rendered answer = CoachAnswerRenderer.render(reply.answer(), facts, message);
      List<String> problems = new ArrayList<>(answer.problems());
      Map<String, CoachChatResponse.Evidence> evidence = new LinkedHashMap<>();
      answer.evidence().forEach(e -> evidence.putIfAbsent(e.label(), e));
      List<CoachAiClient.Recommendation> recommendations = new ArrayList<>();
      for (CoachAiClient.Recommendation recommendation : reply.recommendations()) {
        CoachAnswerRenderer.Rendered reason = CoachAnswerRenderer.render(recommendation.reason(), facts, message);
        problems.addAll(reason.problems());
        reason.evidence().forEach(e -> evidence.putIfAbsent(e.label(), e));
        recommendations.add(new CoachAiClient.Recommendation(recommendation.appId(), reason.text()));
      }
      if (problems.isEmpty()) {
        return new Verified(answer.text(), recommendations, List.copyOf(evidence.values()));
      }
      log.warn("AI 코치 답변의 숫자 확인 실패 {} (시도 {}/{})", problems, attempt, MAX_ATTEMPTS);
    }
    throw AiCoachException.invalidResponse();
  }

  private record Verified(String answer, List<CoachAiClient.Recommendation> recommendations,
                          List<CoachChatResponse.Evidence> evidence) {
  }

  // AI가 추천한 appId가 실제 보유 게임인지 다시 확인하고 화면용 카드로 바꾼다. 보유하지 않은 게임이 섞이면 답 전체를 버린다.
  static List<CoachChatResponse.GameCard> toCards(List<CoachAiClient.Recommendation> recommendations,
                                                  List<SpendingGameResponse> owned) {
    Map<Long, SpendingGameResponse> byAppId = owned.stream()
        .collect(Collectors.toMap(SpendingGameResponse::appId, Function.identity(), (a, b) -> a));
    Set<Long> seen = new HashSet<>();
    List<CoachChatResponse.GameCard> cards = new ArrayList<>();
    for (CoachAiClient.Recommendation recommendation : recommendations) {
      SpendingGameResponse game = byAppId.get(recommendation.appId());
      if (game == null) {
        log.warn("AI 코치가 보유하지 않은 게임을 추천해 답을 버림 appId={}", recommendation.appId());
        throw AiCoachException.invalidResponse();
      }
      if (seen.add(game.appId())) {
        cards.add(new CoachChatResponse.GameCard(game.appId(), game.name(), game.thumbnailUrl(), recommendation.reason()));
      }
    }
    return List.copyOf(cards);
  }

  private static List<CoachChatRequest.Turn> recentHistory(List<CoachChatRequest.Turn> history) {
    if (history == null || history.isEmpty()) {
      return List.of();
    }
    return List.copyOf(history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size()));
  }
}
