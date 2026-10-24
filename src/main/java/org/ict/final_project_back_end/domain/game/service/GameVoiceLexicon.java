package org.ict.final_project_back_end.domain.game.service;

import java.util.*;
import org.ict.final_project_back_end.domain.game.entity.GameSearchName.Kind;
import org.ict.final_project_back_end.domain.game.repository.GameSearchNameRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** STT vocabulary comes from the selected catalog game and its active aliases. */
@Component
public class GameVoiceLexicon {
  private final GameSearchNameRepository names;
  public GameVoiceLexicon(GameSearchNameRepository names) { this.names = names; }
  public static String key(String text) { return GameCatalogNameForms.compact(text); }
  @Transactional(readOnly = true)
  public List<String> hints(String currentName) {
    if (currentName == null || currentName.isBlank()) return List.of();
    var ids = names.exact(GameNameNormalizer.normalize(currentName), Kind.CANONICAL, PageRequest.of(0, 5))
        .stream().map(GameSearchNameRepository.Candidate::getAppId).toList();
    LinkedHashSet<String> result = new LinkedHashSet<>(); result.add(currentName);
    if (!ids.isEmpty()) names.forGames(ids).stream().filter(n -> n.active()).forEach(n -> result.add(n.displayName()));
    return result.stream().map(s -> s.replaceAll("[<>\\r\\n]", " ")).limit(30).toList();
  }
}
