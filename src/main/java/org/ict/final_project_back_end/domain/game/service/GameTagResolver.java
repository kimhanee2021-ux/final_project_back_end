package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 추천/트렌드 목록의 게임에 붙일 대표 태그(장르)를 games 카탈로그에서 찾아 준다.
// games.genre는 "Action, RPG, Indie"처럼 쉼표로 이어진 글자라 쪼개서 쓰고, 화면 카드에는 최대 3개만 내려준다.
// 카탈로그에 없는 게임은 태그가 없는 빈 목록이다(스팀 호출 없이 DB 조회만 한다).
@Component
public class GameTagResolver {

  static final int MAX_TAGS = 3;
  // 장르를 대표하지 못하는 태그는 뒤로 미룬다(분석 화면의 대표 장르 규칙과 같다).
  private static final Set<String> LOW_PRIORITY_TAGS = Set.of("Indie", "Free To Play", "Early Access");

  private final GameRepository gameRepository;

  public GameTagResolver(GameRepository gameRepository) {
    this.gameRepository = gameRepository;
  }

  // appId -> 대표 태그 목록. 카탈로그에 없거나 장르가 비어 있는 게임은 맵에 넣지 않는다.
  public Map<Long, List<String>> resolve(Collection<Long> appIds) {
    Map<Long, List<String>> tagsByAppId = new HashMap<>();
    for (Game game : gameRepository.findAllById(new LinkedHashSet<>(appIds))) {
      List<String> tags = toTags(game.getGenre());
      if (!tags.isEmpty()) {
        tagsByAppId.put(game.getAppId(), tags);
      }
    }
    return tagsByAppId;
  }

  static List<String> toTags(String genre) {
    if (genre == null || genre.isBlank()) {
      return List.of();
    }
    List<String> prioritized = new ArrayList<>();
    List<String> deferred = new ArrayList<>();
    for (String tag : Arrays.stream(genre.split(",")).map(String::trim).filter(t -> !t.isEmpty()).distinct().toList()) {
      (LOW_PRIORITY_TAGS.contains(tag) ? deferred : prioritized).add(tag);
    }
    prioritized.addAll(deferred);
    return prioritized.stream().limit(MAX_TAGS).toList();
  }
}
