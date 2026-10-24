package org.ict.final_project_back_end.domain.gamenews.service;

import java.util.*;
import org.ict.final_project_back_end.domain.game.service.*;
import org.ict.final_project_back_end.domain.gamenews.client.NewsVoiceIntentAiClient;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.*;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.springframework.stereotype.Service;

@Service
public class GameNewsVoiceIntentService {
  private final GameResolutionService games;
  private final NewsVoiceIntentAiClient ai;
  private final NewsVoiceAiLimits limits;
  private final GameCatalogVoiceResolver catalog;
  public GameNewsVoiceIntentService(GameResolutionService games, NewsVoiceIntentAiClient ai, NewsVoiceAiLimits limits, GameCatalogVoiceResolver catalog) {
    this.games = games; this.ai = ai; this.limits = limits; this.catalog = catalog;
  }
  public Response interpret(Request request, String owner, String remote) {
    if (request.transcript() == null || request.transcript().isBlank() || request.transcript().length() > 500
        || request.version() == null || request.version() < 0) throw GameSearchException.invalid("발화는 1~500자이며 유효한 검색 버전이 필요합니다.");
    Context context = request.context() == null ? new Context(null, null, false, null, null, null) : request.context();
    if (context.category() != null) {
      try { GameNewsCategory.valueOf(context.category()); } catch (IllegalArgumentException error) { throw GameSearchException.invalid("허용되지 않은 소식 분류입니다."); }
    }
    if ((context.allCategories() && context.category() != null)
        || (context.awaiting() != null && !Set.of("GAME", "CATEGORY").contains(context.awaiting()))
        || (context.candidateSetToken() != null && (context.candidateSetToken().length() > 100 || context.candidateVersion() == null || context.candidateVersion() < 0)))
      throw GameSearchException.invalid("대화 문맥을 확인할 수 없습니다. 다시 검색해 주세요.");
    List<Turn> history = request.history() == null ? List.of() : request.history();
    if (history.size() > 4) throw GameSearchException.invalid("최근 대화는 4개 발화까지 전달할 수 있습니다.");
    List<Turn> verifiedHistory = new ArrayList<>();
    for (Turn turn : history) {
      if (turn == null || turn.transcript() == null || turn.transcript().isBlank() || turn.transcript().length() > 500
          || turn.action() == null || !Set.of("SEARCH", "CLARIFY", "CHOOSE_GAME", "UNSUPPORTED", "STOP", "REPLAY").contains(turn.action())
          || (turn.name() != null && turn.name().length() > 255)
          || (turn.category() != null && Arrays.stream(GameNewsCategory.values()).noneMatch(value -> value.name().equals(turn.category())))
          || (turn.allCategories() && turn.category() != null)
          || (turn.awaiting() != null && !Set.of("GAME", "CATEGORY").contains(turn.awaiting())))
        throw GameSearchException.invalid("최근 대화 문맥을 확인할 수 없습니다.");
      var priorGame = turn.appId() == null ? null : games.validated(turn.appId().toString());
      verifiedHistory.add(new Turn(turn.transcript(), turn.action(), priorGame == null ? null : priorGame.appId(),
          priorGame == null ? null : priorGame.name(), turn.category(), turn.allCategories(), turn.awaiting()));
    }
    var currentGame = context.appId() == null ? null : games.validated(context.appId().toString());
    var aiContext = new AiContext(currentGame == null ? null : currentGame.appId(), currentGame == null ? null : currentGame.name(),
        context.category(), context.allCategories(), context.awaiting(), List.copyOf(verifiedHistory));
    String text = NewsVoiceRules.clean(request.transcript());
    if (NewsVoiceRules.incompleteCorrection(text)) return new Response("CLARIFY", currentGame == null ? null : currentGame.appId(),
        currentGame == null ? null : currentGame.name(), context.category(), context.allCategories(), currentGame == null ? "GAME" : "CATEGORY",
        List.of(), null, null, false, currentGame == null ? null : currentGame.support(), currentGame == null ? null : currentGame.briefing(),
        "어떤 게임이나 소식 분류로 바꿀까요? 바꿀 내용을 이어서 말씀해 주세요.");
    var ordinal = NewsVoiceRules.ORDINAL.matcher(text);
    if (ordinal.matches()) {
      if (context.candidateSetToken() == null) return clarifyGame(context.category(), context.allCategories(), "선택할 후보가 없어요. 게임명이나 명시적인 Steam 게임 번호를 말씀해 주세요.");
      var selected = games.resolve(new GameResolutionService.Request(null, null, context.candidateSetToken(), Integer.parseInt(ordinal.group(1)), context.candidateVersion()), owner);
      return selected(selected, context.category(), context.allCategories(), request.version(), false);
    }
    var id = NewsVoiceRules.EXPLICIT_ID.matcher(text);
    String explicitId = null;
    if (id.find()) { explicitId = id.group(1); text = text.substring(id.end()).strip(); }
    var directGame = explicitId == null ? null : games.resolve(new GameResolutionService.Request(null, explicitId, null, null, request.version()), owner);
    Slots slots = NewsVoiceRules.extract(text);
    if (slots == null) try (var permit = limits.acquire(owner, remote)) {
      slots = request.context() == null && verifiedHistory.isEmpty() ? ai.extract(text) : ai.extract(text, aiContext);
    }
    if (!slots.command().equals("SEARCH")) {
      if (Set.of("RESET", "CHANGE_GAME").contains(slots.command())) games.clear(owner);
      return new Response(slots.command(), null, null, null, false,
          slots.command().equals("CHANGE_GAME") ? "GAME" : null, List.of(), null, null, false, null, null,
          switch(slots.command()) {
            case "RESET" -> "처음부터 시작할게요. 게임명을 말씀해 주세요.";
            case "CHANGE_GAME" -> "어떤 게임으로 바꿀까요?";
            case "REPLAY" -> "현재 화면의 마지막 요약을 다시 읽습니다.";
            case "STOP" -> "음성 검색을 중지합니다.";
            default -> "저장된 게임 소식을 찾는 요청만 도와드릴 수 있어요. 게임명과 소식 분류를 말씀해 주세요.";
          });
    }
    boolean providedCategory = slots.category() != null || slots.allCategories();
    boolean draftCategory = "GAME".equals(context.awaiting()) || NewsVoiceRules.isCorrection(text) || slots.useCurrentGame();
    String category = providedCategory ? slots.category() : draftCategory ? context.category() : null;
    boolean all = providedCategory ? slots.allCategories() : draftCategory && context.allCategories();
    if (slots.multipleCategories()) { category = null; all = false; }
    GameResolutionService.Response game = directGame;
    if (game == null && slots.gameQuery() != null) game = catalog.resolve(slots.gameQuery(), request.transcript(), aiContext, request.version(), owner, remote);
    else if (game == null && currentGame != null) game = currentGame;
    if (game == null) return clarifyGame(category, all, "어떤 게임의 소식이 궁금한가요? 게임명을 말씀해 주세요.");
    return selected(game, category, all, request.version(), slots.multipleCategories());
  }
  private Response selected(GameResolutionService.Response game, String category, boolean all, long version, boolean multiple) {
    if (game.action().equals("NOT_FOUND")) return clarifyGame(category, all, game.message());
    if (game.action().equals("CHOOSE_GAME")) return new Response("CHOOSE_GAME", null, null, category, all, "GAME",
        game.candidates(), game.candidateSetToken(), version, game.hasMore(), null, null, game.message());
    if (category == null && !all) return new Response("CLARIFY", game.appId(), game.name(), null, false, "CATEGORY",
        List.of(), null, null, false, game.support(), game.briefing(), multiple
          ? "한 번에 한 분류를 골라 주세요. 예를 들어 패치 또는 이벤트라고 말씀해 주세요."
          : game.name() + ". 어떤 소식을 들을까요? 패치, 이벤트 또는 전체 소식처럼 말씀해 주세요.");
    if (!game.support().equals("SUPPORTED")) return new Response("UNSUPPORTED", game.appId(), game.name(), category, all, null,
        List.of(), null, null, false, game.support(), game.briefing(), game.support().equals("UNKNOWN")
          ? "이 게임의 뉴스 지원 여부가 아직 확인되지 않았어요. 기존 게시판 검색에서 저장된 소식을 확인해 주세요."
          : "이 게임은 뉴스 수집 미지원 상태예요. 기존 게시판에서 저장된 소식을 확인할 수 있어요.");
    return new Response("SEARCH", game.appId(), game.name(), category, all, null, List.of(), null, null, false,
        game.support(), game.briefing(), "저장된 소식을 조회합니다.");
  }
  private Response clarifyGame(String category, boolean all, String message) {
    return new Response("CLARIFY", null, null, category, all, "GAME", List.of(), null, null, false, null, null, message);
  }
}
