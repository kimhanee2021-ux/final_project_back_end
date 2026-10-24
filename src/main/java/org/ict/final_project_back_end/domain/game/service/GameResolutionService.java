package org.ict.final_project_back_end.domain.game.service;

import java.util.*;
import org.ict.final_project_back_end.domain.gamenews.repository.*;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSupport.Status;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Shared by the legacy game resolver and the voice-intent orchestrator. */
@Service
public class GameResolutionService {
  public record Request(String gameQuery, String appId, String candidateSetToken, Integer selectedCandidateIndex, Long version) {}
  public record Response(String action, Long appId, String name, String support, String briefing,
      List<GameNameResolver.Candidate> candidates, String candidateSetToken, boolean hasMore, String message) {}
  private final GameNameResolver resolver;
  private final GameIdentityValidator validator;
  private final GameCandidateSessionService sessions;
  private final GameNewsSupportRepository support;
  private final GameNewsBriefingRepository briefings;
  public GameResolutionService(GameNameResolver resolver, GameIdentityValidator validator,
      GameCandidateSessionService sessions, GameNewsSupportRepository support, GameNewsBriefingRepository briefings) {
    this.resolver = resolver; this.validator = validator; this.sessions = sessions; this.support = support; this.briefings = briefings;
  }
  public Response resolve(Request body, String owner) {
    if (body.version() == null || body.version() < 0) throw GameSearchException.invalid("검색 버전이 필요합니다.");
    int modes = (body.gameQuery() != null ? 1 : 0) + (body.appId() != null ? 1 : 0)
        + (body.candidateSetToken() != null || body.selectedCandidateIndex() != null ? 1 : 0);
    if (modes != 1) throw GameSearchException.invalid("게임명·게임 번호·후보 선택 중 하나만 전달해 주세요.");
    if (body.appId() != null) { sessions.clear(owner); return validated(body.appId()); }
    if (body.gameQuery() != null) {
      sessions.clear(owner);
      var result = resolver.resolve(body.gameQuery());
      if (result.autoSelect()) return validated(Long.toString(result.candidates().getFirst().appId()));
      if (result.candidates().isEmpty()) return new Response("NOT_FOUND", null, null, null, null, List.of(), null, false,
          "일치하는 게임을 찾지 못했어요. 다른 게임명이나 Steam 게임 번호로 검색해 주세요.");
      String token = sessions.issue(owner, body.version(), result.candidates());
      return new Response("CHOOSE_GAME", null, null, null, null, result.candidates(), token, result.hasMore(), "검색된 게임을 선택해 주세요.");
    }
    if (body.candidateSetToken() == null || body.candidateSetToken().length() > 100 || body.selectedCandidateIndex() == null)
      throw GameSearchException.invalid("후보 토큰과 번호가 필요합니다.");
    return validated(Long.toString(sessions.select(owner, body.version(), body.candidateSetToken(), body.selectedCandidateIndex())));
  }
  public void clear(String owner) { sessions.clear(owner); }
  public Response offerCandidates(List<GameNameResolver.Candidate> candidates, long version, String owner, boolean hasMore) {
    sessions.clear(owner);
    if (candidates.isEmpty()) return new Response("NOT_FOUND", null, null, null, null, List.of(), null, false,
        "게임을 확정하지 못했어요. 정식 게임명이나 Steam 게임 번호로 다시 말씀해 주세요.");
    var visible = List.copyOf(candidates.subList(0, Math.min(5, candidates.size())));
    String token = sessions.issue(owner, version, visible);
    return new Response("CHOOSE_GAME", null, null, null, null, visible, token, hasMore || candidates.size() > 5,
        "어떤 게임을 말씀하셨나요? 후보 번호나 더 구체적인 게임명을 말씀해 주세요.");
  }
  public Response validated(String id) {
    var game = validator.require(id);
    Status policy = support.findById(game.getAppId()).map(s -> s.status()).orElse(Status.UNKNOWN);
    boolean stored = briefings.findPublishedSummaries(game.getAppId(), null, PageRequest.of(0, 1)).hasContent();
    String message = switch (policy) {
      case UNSUPPORTED -> "뉴스 수집 미지원 게임입니다. 저장된 소식은 게시판에서 확인할 수 있어요.";
      case UNKNOWN -> "뉴스 수집 지원 여부가 아직 확인되지 않았어요. 저장된 소식을 조회합니다.";
      case SUPPORTED -> stored ? "게임을 선택했어요." : "게임을 선택했어요. 아직 저장된 브리핑이 없어요.";
    };
    return new Response("SELECTED", game.getAppId(), game.getName(), policy.name(), stored ? "AVAILABLE" : "NO_STORED_BRIEFING",
        List.of(), null, false, message);
  }
}
