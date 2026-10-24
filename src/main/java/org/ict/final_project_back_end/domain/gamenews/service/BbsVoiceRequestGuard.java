package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 서버 인스턴스별 제한. 공개 BBS의 비용 제한이며 영구 사용량 집계가 아니다. */
@Component
public class BbsVoiceRequestGuard {
  private final int dailyLimit, minuteLimit;
  private final Semaphore slots;
  private final Clock clock;
  private final Map<String, Integer> addresses = new HashMap<>();
  private LocalDate day;
  private long minute = -1;
  private int used;

  @Autowired
  public BbsVoiceRequestGuard(@Value("${app.bbs.voice.daily-calls:200}") int daily,
      @Value("${app.bbs.voice.calls-per-minute:6}") int perMinute,
      @Value("${app.bbs.voice.concurrent-calls:2}") int concurrent) {
    this(daily, perMinute, concurrent, Clock.systemUTC());
  }
  BbsVoiceRequestGuard(int daily, int perMinute, int concurrent, Clock clock) {
    if (daily < 1 || perMinute < 1 || concurrent < 1) throw new IllegalArgumentException("BBS voice limits must be positive");
    dailyLimit = daily; minuteLimit = perMinute; slots = new Semaphore(concurrent); this.clock = clock;
  }

  public synchronized Permit acquire(String address) {
    var now = clock.instant();
    var today = now.atOffset(ZoneOffset.UTC).toLocalDate();
    if (!today.equals(day)) { day = today; used = 0; }
    long currentMinute = now.getEpochSecond() / 60;
    if (currentMinute != minute) { minute = currentMinute; addresses.clear(); }
    int count = addresses.getOrDefault(address, 0);
    if (used >= dailyLimit || count >= minuteLimit || addresses.size() >= 1024 && count == 0 || !slots.tryAcquire())
      throw BbsVoiceException.limited();
    used++; addresses.put(address, count + 1);
    return new Permit();
  }

  public final class Permit implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    @Override public void close() { if (closed.compareAndSet(false, true)) slots.release(); }
  }
}
