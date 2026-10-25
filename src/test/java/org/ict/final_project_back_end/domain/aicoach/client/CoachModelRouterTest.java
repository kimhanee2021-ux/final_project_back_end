package org.ict.final_project_back_end.domain.aicoach.client;

import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

// 공급자 순서와 전환 규칙을 가짜 모델로 검증한다 (네트워크 없이)
class CoachModelRouterTest {

  private static final CoachModel.Request REQUEST = new CoachModel.Request("규칙", List.of(), Map.of(), 0.1, 100);

  private final List<String> called = new ArrayList<>();

  // 부르면 이름을 기록하고, 정해 둔 답을 주거나 예외를 던지는 가짜 공급자
  private CoachModel fake(String name, boolean configured, Supplier<String> answer) {
    return new CoachModel() {
      public String provider() {
        return name;
      }

      public boolean configured() {
        return configured;
      }

      public String generateJson(Request request) {
        called.add(name);
        return answer.get();
      }
    };
  }

  @Test
  void usesProvidersInTheGivenOrder() {
    CoachModelRouter router = new CoachModelRouter(List.of(fake("gemini", true, () -> "G"), fake("groq", true, () -> "Q")));
    assertEquals("Q", router.generateJson(List.of("groq", "gemini"), "test", REQUEST));
    assertEquals(List.of("groq"), called);
  }

  @Test
  void fallsBackWhenTheFirstProviderIsBusyOrLimited() {
    CoachModelRouter router = new CoachModelRouter(List.of(
        fake("gemini", true, () -> { throw AiCoachException.tooManyRequests(); }),
        fake("groq", true, () -> "Q")));
    assertEquals("Q", router.generateJson(List.of("gemini", "groq"), "test", REQUEST));
    assertEquals(List.of("gemini", "groq"), called);
  }

  @Test
  void skipsProvidersWithoutKeys() {
    CoachModelRouter router = new CoachModelRouter(List.of(fake("gemini", false, () -> "G"), fake("groq", true, () -> "Q")));
    assertEquals("Q", router.generateJson(List.of("gemini", "groq"), "test", REQUEST));
    assertEquals(List.of("groq"), called);
  }

  @Test
  void doesNotFallBackOnInvalidAnswers() {
    CoachModelRouter router = new CoachModelRouter(List.of(
        fake("gemini", true, () -> { throw AiCoachException.invalidResponse(); }),
        fake("groq", true, () -> "Q")));
    AiCoachException e = assertThrows(AiCoachException.class,
        () -> router.generateJson(List.of("gemini", "groq"), "test", REQUEST));
    assertEquals("AI_INVALID_RESPONSE", e.getCode());
    assertEquals(List.of("gemini"), called);
  }

  @Test
  void reportsTheLastFailureWhenEveryProviderFails() {
    CoachModelRouter router = new CoachModelRouter(List.of(
        fake("gemini", true, () -> { throw AiCoachException.busy(); }),
        fake("groq", true, () -> { throw AiCoachException.tooManyRequests(); })));
    AiCoachException e = assertThrows(AiCoachException.class,
        () -> router.generateJson(List.of("gemini", "groq"), "test", REQUEST));
    assertEquals("AI_RATE_LIMITED", e.getCode());
  }

  @Test
  void noConfiguredProviderMeansNotConfigured() {
    CoachModelRouter router = new CoachModelRouter(List.of(fake("gemini", false, () -> "G")));
    AiCoachException e = assertThrows(AiCoachException.class,
        () -> router.generateJson(List.of("gemini", "unknown"), "test", REQUEST));
    assertEquals("AI_NOT_CONFIGURED", e.getCode());
  }

  @Test
  void parsesProviderOrderLoosely() {
    assertEquals(List.of("groq", "gemini"), CoachModelRouter.parseOrder(" Groq, ,gemini "));
    assertEquals(List.of(), CoachModelRouter.parseOrder(null));
  }
}
