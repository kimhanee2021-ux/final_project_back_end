package org.ict.final_project_back_end.domain.gamenews.service;

import java.util.*;
import org.ict.final_project_back_end.domain.game.service.*;
import org.ict.final_project_back_end.domain.gamenews.client.GameCatalogAiClient;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class GameCatalogVoiceResolver {
  private final GameNameResolver names;
  private final GameResolutionService games;
  private final GameCatalogAiClient ai;
  private final NewsVoiceAiLimits limits;
  public GameCatalogVoiceResolver(GameNameResolver names, GameResolutionService games, GameCatalogAiClient ai, NewsVoiceAiLimits limits) {
    this.names = names; this.games = games; this.ai = ai; this.limits = limits;
  }
  public GameResolutionService.Response resolve(String query, String transcript, AiContext context, long version, String owner, String remote) {
    var local = names.resolve(query);
    if (local.autoSelect()) {
      games.clear(owner); return games.validated(Long.toString(local.candidates().getFirst().appId()));
    }
    Map<Long, GameNameResolver.Candidate> candidates = new LinkedHashMap<>();
    local.candidates().forEach(c -> candidates.put(c.appId(), c));
    boolean more = local.hasMore();
    boolean shortQuery = query.matches("[a-zA-Z0-9]{2,8}");
    try {
      if (candidates.isEmpty() || (shortQuery && (candidates.size() > 1 || more))) {
        List<String> variants;
        try (var permit = limits.acquire(owner, remote)) { variants = ai.expand(query, context); }
        if (variants == null || variants.size() > 4) variants = List.of();
        String numbers = query.replaceAll("[^0-9]", "");
        Map<Long, GameNameResolver.Candidate> precise = new LinkedHashMap<>(), expanded = new LinkedHashMap<>();
        boolean expandedMore = false;
        for (String variant : variants) {
          if (variant == null || variant.isBlank() || variant.length() > 255
              || (!numbers.isEmpty() && !GameCatalogNameForms.sameNumbers(query, variant))) continue;
          var found = names.resolve(variant);
          expandedMore |= found.hasMore();
          for (var candidate : found.candidates()) {
            var c = new GameNameResolver.Candidate(candidate.appId(), candidate.name(), "MODEL_QUERY");
            if (found.autoSelect() || candidate.match().equals("CANONICAL")) precise.putIfAbsent(c.appId(), c);
            if (found.autoSelect() || Set.of("CANONICAL", "PREFIX", "PARTIAL").contains(candidate.match())) expanded.putIfAbsent(c.appId(), c);
          }
        }
        if (!precise.isEmpty() && !shortQuery) {
          // Full translated names outrank ambiguous abbreviation variants from the same expansion.
          candidates.clear(); candidates.putAll(precise); more = precise.size() > 5;
        } else {
          var localCandidates = new LinkedHashMap<>(candidates);
          candidates.clear(); candidates.putAll(precise); expanded.forEach(candidates::putIfAbsent);
          localCandidates.forEach(candidates::putIfAbsent); more |= expandedMore;
        }
      }
      var choices = candidates.values().stream().limit(5).toList();
      more |= candidates.size() > 5;
      if (!choices.isEmpty()) {
        GameCatalogAiClient.Decision decision;
        try (var permit = limits.acquire(owner, remote)) { decision = ai.choose(transcript, context, choices); }
        if (decision != null && !decision.ambiguous() && decision.selectedIndex() != null
            && decision.selectedIndex() >= 1 && decision.selectedIndex() <= choices.size() && !more
            && choices.stream().noneMatch(c -> c.match().equals("PHONETIC_SUGGESTION"))
            && choices.stream().map(c -> GameVoiceLexicon.key(c.name())).distinct().count() == choices.size()
            && !(choices.size() > 1 && context.appId() == null && choices.stream().allMatch(c ->
                GameCatalogNameForms.abbreviations(c.name()).contains(GameVoiceLexicon.key(query))))) {
          var selected = choices.get(decision.selectedIndex() - 1);
          if (selected.name().matches("(?i).*\\b(?:playtest|demo|soundtrack|dlc)\\b.*")
              && !transcript.matches("(?i).*(?:playtest|demo|soundtrack|dlc|플레이테스트|데모|사운드트랙).*"))
            return games.offerCandidates(choices, version, owner, more);
          games.clear(owner); return games.validated(Long.toString(selected.appId()));
        }
      }
    } catch (BusinessException unavailable) {
      // Provider failures cannot turn uncertain names into confirmed identities.
    }
    return games.offerCandidates(List.copyOf(candidates.values()), version, owner, more);
  }
}
