package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.ict.final_project_back_end.domain.gamenews.client.NewsSpeechClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NewsSpeechService {
  private record Cached(byte[] bytes, Instant expires) { }
  private record Quota(long minute, int count) { }
  private final NewsSpeechClient client;
  private final Semaphore slots = new Semaphore(2);
  private final Map<String, Cached> cache = new LinkedHashMap<>(32, 0.75f, true);
  private final Map<String, Quota> callers = new HashMap<>();
  private LocalDate budgetDay = LocalDate.now(ZoneOffset.UTC);
  private int characters;
  public NewsSpeechService(NewsSpeechClient client) { this.client = client; }
  public byte[] speak(String input, String caller) {
    if (input == null || input.isBlank() || input.length() > 2400 || input.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
      throw new GameSearchException(HttpStatus.BAD_REQUEST, "INVALID_SPEECH", "읽을 문장은 1~2,400자로 보내 주세요.");
    String text = input.strip();
    synchronized (this) {
      cache.entrySet().removeIf(entry -> entry.getValue().expires().isBefore(Instant.now()));
      Cached hit = cache.get(text);
      if (hit != null) return hit.bytes();
    }
    if (!slots.tryAcquire()) throw limited();
    try {
      synchronized (this) {
        long minute = System.currentTimeMillis() / 60000;
        callers.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
        Quota quota = callers.get(caller);
        int count = quota == null ? 0 : quota.count();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(budgetDay)) { budgetDay = today; characters = 0; }
        if (count >= 10 || callers.size() >= 1000 || characters + text.length() > 60000) throw limited();
        callers.put(caller, new Quota(minute, count + 1)); characters += text.length();
      }
      byte[] bytes = client.synthesize(text);
      if (bytes == null || bytes.length < 44 || bytes.length > 8_000_044) throw new GameSearchException(HttpStatus.BAD_GATEWAY, "SPEECH_UNAVAILABLE", "음성 데이터를 준비하지 못했어요.");
      synchronized (this) {
        cache.put(text, new Cached(bytes, Instant.now().plusSeconds(3600)));
        while (cache.size() > 32 || cache.values().stream().mapToLong(value -> value.bytes().length).sum() > 16_000_000)
          cache.remove(cache.keySet().iterator().next());
      }
      return bytes;
    } finally { slots.release(); }
  }
  private static GameSearchException limited() { return new GameSearchException(HttpStatus.TOO_MANY_REQUESTS,
      "SPEECH_LIMIT", "음성 요청이 많아요. 잠시 후 다시 듣기를 누르거나 화면의 요약을 확인해 주세요."); }
}
