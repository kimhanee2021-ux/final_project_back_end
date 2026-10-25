package org.ict.final_project_back_end.domain.wishsales.client;

import org.ict.final_project_back_end.domain.wishsales.dto.BuyAdviceDtos.BuyAdviceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

// 공급자를 AI_PROVIDERS 순서대로 시도하고, 키가 없으면 건너뛰고, 실패하면 다음 공급자로 넘어가는지
class AdviceSentenceClientTest {

  private record FakeProvider(String name, boolean configured, String answer, List<String> calls) implements AdviceTextProvider {
    @Override
    public String complete(String prompt) {
      calls.add(name);
      if (answer == null) {
        throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limit", null, null, null);
      }
      return answer;
    }
  }

  private final List<BuyAdviceResponse> items = List.of(mock(BuyAdviceResponse.class), mock(BuyAdviceResponse.class));

  @Test
  void triesProvidersInOrderSkippingMissingKeysAndFailures() {
    List<String> calls = new ArrayList<>();
    AdviceSentenceClient client = new AdviceSentenceClient("anthropic, Gemini ,groq", List.of(
        new FakeProvider("anthropic", false, "{}", calls),
        new FakeProvider("gemini", true, null, calls),
        new FakeProvider("groq", true, "```json\n{\"overall\":\"요약\",\"sentences\":[\"a\",\"b\"]}\n```", calls)));

    AdviceSentenceClient.BatchResult result = client.explainBatch(items);

    assertEquals(List.of("gemini", "groq"), calls);
    assertEquals("요약", result.overall());
    assertEquals(List.of("a", "b"), result.sentences());
  }

  @Test
  void wrongSentenceCountFallsThroughAndAllFailingReturnsNull() {
    List<String> calls = new ArrayList<>();
    AdviceSentenceClient client = new AdviceSentenceClient("gemini,groq", List.of(
        new FakeProvider("gemini", true, "[\"only one\"]", calls),
        new FakeProvider("groq", true, null, calls)));

    assertNull(client.explainBatch(items));
    assertEquals(List.of("gemini", "groq"), calls);
  }

  @Test
  void notConfiguredWithoutAnyKeyAndSkipsEmptyRequests() {
    AdviceSentenceClient client = new AdviceSentenceClient("gemini",
        List.of(new FakeProvider("gemini", false, "{}", new ArrayList<>())));
    assertFalse(client.isConfigured());
    assertNull(client.explainBatch(items));
    assertNull(new AdviceSentenceClient("gemini", List.of(new FakeProvider("gemini", true, "{}", new ArrayList<>())))
        .explainBatch(List.of()));
  }
}
