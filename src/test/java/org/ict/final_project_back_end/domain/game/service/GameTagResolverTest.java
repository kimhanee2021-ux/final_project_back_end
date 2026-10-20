package org.ict.final_project_back_end.domain.game.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameTagResolverTest {

  @Test
  void splitsGenreTextIntoAtMostThreeTags() {
    assertEquals(List.of("Action", "RPG", "Adventure"),
        GameTagResolver.toTags("Action, RPG, Adventure, Strategy"));
  }

  @Test
  void pushesLowPriorityTagsToTheEnd() {
    assertEquals(List.of("Action", "RPG", "Indie"), GameTagResolver.toTags("Indie, Action, RPG"));
  }

  @Test
  void removesDuplicatesAndBlanks() {
    assertEquals(List.of("RPG", "Action"), GameTagResolver.toTags("RPG, , RPG, Action"));
  }

  @Test
  void returnsEmptyListWhenGenreIsMissing() {
    assertEquals(List.of(), GameTagResolver.toTags(null));
    assertEquals(List.of(), GameTagResolver.toTags("  "));
  }
}
