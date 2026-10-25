package org.ict.final_project_back_end.domain.search.service;

import org.ict.final_project_back_end.domain.search.exception.SearchException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchRateLimitTest {

  private final SearchRateLimit limit = new SearchRateLimit();

  @Test
  void allowsUpToTheLimitPerMinuteThenRejects() {
    for (int i = 0; i < SearchRateLimit.MAX_PER_MINUTE; i++) {
      limit.check(1L);
    }

    SearchException e = assertThrows(SearchException.class, () -> limit.check(1L));
    assertEquals("SEARCH_RATE_LIMITED", e.getCode());
    assertEquals(429, e.getStatus().value());
  }

  @Test
  void eachUserHasTheirOwnCount() {
    for (int i = 0; i < SearchRateLimit.MAX_PER_MINUTE; i++) {
      limit.check(1L);
    }

    assertDoesNotThrow(() -> limit.check(2L));
  }
}
