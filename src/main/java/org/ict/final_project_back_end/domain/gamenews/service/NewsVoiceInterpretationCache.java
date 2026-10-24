package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.*;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.*;
import org.springframework.stereotype.Component;

/** Bounded, memory-only cache of interpretation, never of selected IDs or candidate tokens. */
@Component
public class NewsVoiceInterpretationCache {
  private record Key(String text, AiContext context) {}
  private record Entry(Slots slots, long expires) {}
  private final Map<Key, Entry> entries = new LinkedHashMap<>(32, .75f, true);
  private final Clock clock;
  public NewsVoiceInterpretationCache() { this(Clock.systemUTC()); }
  NewsVoiceInterpretationCache(Clock clock) { this.clock = clock; }
  public synchronized Slots get(String text, AiContext context) {
    entries.entrySet().removeIf(e -> e.getValue().expires() <= clock.millis());
    var entry = entries.get(new Key(text, context));
    return entry == null ? null : entry.slots();
  }
  public synchronized void put(String text, AiContext context, Slots slots) {
    if (slots == null) return;
    entries.put(new Key(text, context), new Entry(slots, clock.millis() + 300_000));
    while (entries.size() > 256) entries.remove(entries.keySet().iterator().next());
  }
}
