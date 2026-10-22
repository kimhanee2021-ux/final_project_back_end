package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// 만든 AI 분석 결과를 회원·주제별로 하나씩 서버 메모리에 보관한다 (Gemini 비용·대기 시간 절약).
// 소비 분석 데이터가 달라지면(fingerprint가 바뀌면) 다시 만들고, 서버를 다시 켜면 초기화된다.
@Component
public class CoachInsightCache {

  private record Key(Long userId, InsightTopic topic) {
  }

  private record Entry(int fingerprint, CoachInsightResponse insight) {
  }

  private final Map<Key, Entry> entries = new HashMap<>();

  public synchronized Optional<CoachInsightResponse> find(Long userId, InsightTopic topic, int fingerprint) {
    Entry entry = entries.get(new Key(userId, topic));
    return entry != null && entry.fingerprint() == fingerprint ? Optional.of(entry.insight()) : Optional.empty();
  }

  public synchronized void put(Long userId, InsightTopic topic, int fingerprint, CoachInsightResponse insight) {
    entries.put(new Key(userId, topic), new Entry(fingerprint, insight));
  }
}
