package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.game.service.GameCatalogNameForms;
import org.ict.final_project_back_end.domain.game.service.GameNameResolver;
import org.ict.final_project_back_end.domain.game.service.GameResolutionService;
import org.ict.final_project_back_end.domain.game.service.GameVoiceLexicon;
import org.ict.final_project_back_end.domain.gamenews.client.catalog.GameCatalogAiClient;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GameCatalogVoiceResolver {
  private final GameNameResolver names;
  private final GameResolutionService games;
  private final GameCatalogAiClient ai;
  private final NewsVoiceAiLimits limits;

  public GameCatalogVoiceResolver(
      GameNameResolver names,
      GameResolutionService games,
      GameCatalogAiClient ai,
      NewsVoiceAiLimits limits) {
    this.names = names;
    this.games = games;
    this.ai = ai;
    this.limits = limits;
  }

  public boolean needsExpansion(String query) {
    var local = names.resolve(query);
    return local.candidates().isEmpty()
        || (query.matches("[a-zA-Z0-9]{2,8}")
            && local.candidates().stream()
                .anyMatch(
                    c ->
                        GameCatalogNameForms.abbreviations(c.name())
                            .contains(GameVoiceLexicon.key(query)))
            && (local.candidates().size() > 1 || local.hasMore()));
  }

  public GameResolutionService.Response resolve(
      String query,
      String transcript,
      AiContext context,
      long version,
      String owner,
      String remote) {
    return resolve(query, transcript, context, version, owner, remote, null, false);
  }

  public GameResolutionService.Response resolve(
      String query,
      String transcript,
      AiContext context,
      long version,
      String owner,
      String remote,
      List<String> interpretedQueries,
      boolean gameAmbiguous) {
    var local = names.resolve(query);
    if (local.autoSelect() && !gameAmbiguous) {
      games.clear(owner);
      return games.validated(Long.toString(local.candidates().getFirst().appId()));
    }
    Map<Long, GameNameResolver.Candidate> candidates = new LinkedHashMap<>();
    local.candidates().forEach(c -> candidates.put(c.appId(), c));
    boolean more = local.hasMore();
    boolean shortQuery = query.matches("[a-zA-Z0-9]{2,8}");
    try {
      if (needsExpansion(query)) {
        List<String> variants;
        if (interpretedQueries != null) variants = interpretedQueries;
        else
          try (var permit = limits.acquire(owner, remote)) {
            variants = ai.expand(query, context);
          }
        if (variants == null || variants.size() > 4) variants = List.of();
        String numbers = query.replaceAll("[^0-9]", "");
        Map<Long, GameNameResolver.Candidate> precise = new LinkedHashMap<>(),
            expanded = new LinkedHashMap<>();
        boolean expandedMore = false;
        for (String variant : variants) {
          if (variant == null
              || variant.isBlank()
              || variant.length() > 255
              || (!numbers.isEmpty() && !GameCatalogNameForms.sameNumbers(query, variant)))
            continue;
          var found = names.resolve(variant);
          expandedMore |= found.hasMore();
          for (var candidate : found.candidates()) {
            var c =
                new GameNameResolver.Candidate(candidate.appId(), candidate.name(), "MODEL_QUERY");
            if (found.autoSelect() || candidate.match().equals("CANONICAL"))
              precise.putIfAbsent(c.appId(), c);
            if (found.autoSelect()
                || Set.of("CANONICAL", "PREFIX", "PARTIAL").contains(candidate.match()))
              expanded.putIfAbsent(c.appId(), c);
          }
        }
        if (!precise.isEmpty() && !shortQuery) {
          // 확장된 검색어 중 정확한 정식명 일치를 모호한 약칭 후보보다 우선합니다.
          candidates.clear();
          candidates.putAll(precise);
          more = precise.size() > 5;
        } else {
          var localCandidates = new LinkedHashMap<>(candidates);
          candidates.clear();
          candidates.putAll(precise);
          expanded.forEach(candidates::putIfAbsent);
          localCandidates.forEach(candidates::putIfAbsent);
          more |= expandedMore;
        }
      }
      var choices = candidates.values().stream().limit(5).toList();
      more |= candidates.size() > 5;
      if (!gameAmbiguous
          && interpretedQueries != null
          && choices.size() == 1
          && !more
          && choices.getFirst().match().equals("MODEL_QUERY")
          && !shortQuery
          && !choices.getFirst().name().matches("(?i).*\\b(?:playtest|demo|soundtrack|dlc)\\b.*")
          && interpretedQueries.stream()
              .anyMatch(
                  v -> {
                    var found = names.resolve(v);
                    return found.autoSelect()
                        && found.candidates().getFirst().appId() == choices.getFirst().appId()
                        && found.candidates().getFirst().match().equals("CANONICAL");
                  })) {
        games.clear(owner);
        return games.validated(Long.toString(choices.getFirst().appId()));
      }
      if (gameAmbiguous) return games.offerCandidates(choices, version, owner, more);
      if (!choices.isEmpty()) {
        GameCatalogAiClient.Decision decision;
        try (var permit = limits.acquire(owner, remote)) {
          decision = ai.choose(transcript, context, choices);
        }
        if (decision != null
            && !decision.ambiguous()
            && decision.selectedIndex() != null
            && decision.selectedIndex() >= 1
            && decision.selectedIndex() <= choices.size()
            && !more
            && choices.stream().noneMatch(c -> c.match().equals("PHONETIC_SUGGESTION"))
            && choices.stream().map(c -> GameVoiceLexicon.key(c.name())).distinct().count()
                == choices.size()
            && !(choices.size() > 1
                && context.appId() == null
                && choices.stream()
                    .allMatch(
                        c ->
                            GameCatalogNameForms.abbreviations(c.name())
                                .contains(GameVoiceLexicon.key(query))))) {
          var selected = choices.get(decision.selectedIndex() - 1);
          if (selected.name().matches("(?i).*\\b(?:playtest|demo|soundtrack|dlc)\\b.*")
              && !transcript.matches("(?i).*(?:playtest|demo|soundtrack|dlc|플레이테스트|데모|사운드트랙).*"))
            return games.offerCandidates(choices, version, owner, more);
          games.clear(owner);
          return games.validated(Long.toString(selected.appId()));
        }
      }
    } catch (BusinessException unavailable) {
      // 모델 호출에 실패하면 불확실한 게임명을 확정하지 않고 후보 확인으로 이어갑니다.
    }
    return games.offerCandidates(List.copyOf(candidates.values()), version, owner, more);
  }
}
