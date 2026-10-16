package org.ict.final_project_back_end.domain.aiCoach.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.analysis.service.SpendingAnalysisService;
import org.ict.final_project_back_end.domain.budget.service.BudgetService;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.global.exception.AiCoachException;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
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
  // 금액이 틀린 답은 한 번만 다시 묻는다 (Gemini 호출 비용·대기 시간)
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
      CoachAiClient.Reply reply = replyWithCheckedAmounts(facts, recentHistory(request.history()), request.message().trim());
      return new CoachChatResponse(reply.answer(), toCards(reply.recommendations(), spending.games()));
    } finally {
      rateLimit.end(userId);
    }
  }

  // 답변 속 금액이 facts와 다르면(옮겨 적다 틀림) 한 번 더 묻고, 그래도 틀리면 답을 보여주지 않는다
  private CoachAiClient.Reply replyWithCheckedAmounts(Map<String, Object> facts, List<CoachChatRequest.Turn> history,
                                                      String message) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      CoachAiClient.Reply reply = aiClient.reply(facts, history, message);
      Set<Long> unknown = CoachAnswerGuard.unknownAmounts(reply.answer(), facts);
      if (unknown.isEmpty()) {
        return reply;
      }
      log.warn("AI 코치 답변에 계산되지 않은 금액 {} (시도 {}/{})", unknown, attempt, MAX_ATTEMPTS);
    }
    throw AiCoachException.invalidResponse();
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
