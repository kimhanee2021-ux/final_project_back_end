package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Bound expensive fallback calls only. Rule-based commands need no provider budget. */
@Component
public class NewsVoiceAiLimits {
  private record Window(long minute, int count) {}
  private final Map<String, Window> windows = new HashMap<>();
  private final Semaphore concurrent;
  private final int dailyLimit, minuteLimit;
  private final Clock clock;
  private LocalDate day;
  private int daily;
  @Autowired public NewsVoiceAiLimits(@Value("${app.news.voice.daily-ai-calls:200}") int dailyLimit,
      @Value("${app.news.voice.ai-calls-per-minute:20}") int minuteLimit,
      @Value("${app.news.voice.concurrent-ai-calls:2}") int concurrency) {
    this(dailyLimit, minuteLimit, concurrency, Clock.systemUTC());
  }
  NewsVoiceAiLimits(int dailyLimit, int minuteLimit, int concurrency, Clock clock) {
    this.dailyLimit = Math.max(0, dailyLimit); this.minuteLimit = Math.max(0, minuteLimit);
    this.concurrent = new Semaphore(Math.max(1, concurrency)); this.clock = clock;
  }
  public synchronized Permit acquire(String owner, String remote) {
    LocalDate today = LocalDate.now(clock);
    if (!today.equals(day)) { day = today; daily = 0; }
    long minute = clock.instant().getEpochSecond() / 60;
    windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
    String[] keys = {"session:" + owner, "remote:" + remote};
    if (daily >= dailyLimit || windows.size() > 10000) throw limited();
    for (String key : keys) if (windows.getOrDefault(key, new Window(minute, 0)).count() >= minuteLimit) throw limited();
    if (!concurrent.tryAcquire()) throw limited();
    daily++;
    for (String key : keys) windows.put(key, new Window(minute, windows.getOrDefault(key, new Window(minute, 0)).count() + 1));
    return new Permit(concurrent);
  }
  private GameSearchException limited() { return new GameSearchException(HttpStatus.TOO_MANY_REQUESTS, "VOICE_AI_BUDGET", "음성 의도 해석 요청 한도에 도달했어요. 짧은 게임명·분류로 말씀하거나 기존 검색창을 이용해 주세요."); }
  public static final class Permit implements AutoCloseable {
    private final Semaphore semaphore;
    private final AtomicBoolean closed = new AtomicBoolean();
    Permit(Semaphore semaphore) { this.semaphore = semaphore; }
    @Override public void close() { if (closed.compareAndSet(false, true)) semaphore.release(); }
  }
}
