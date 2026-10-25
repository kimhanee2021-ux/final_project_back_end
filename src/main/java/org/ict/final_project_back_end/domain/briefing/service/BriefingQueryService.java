package org.ict.final_project_back_end.domain.briefing.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.ict.final_project_back_end.domain.briefing.entity.GameBriefing;
import org.ict.final_project_back_end.domain.briefing.entity.OfficialNews;
import org.ict.final_project_back_end.domain.briefing.repository.GameBriefingRepository;
import org.ict.final_project_back_end.domain.briefing.repository.OfficialNewsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// 저장된 AI 게임 브리핑을 화면용 카드로 조회한다.
// 카드마다 그 게임의 최신 공식 글 5개와 대조해, 원문이 바뀌었으면(stale) 분석 내용을 비우고,
// 분류(group)·소주제(topic)로 고른 뒤, 근거 글이 최근인 카드부터 한 쪽에 PAGE_SIZE개씩 보여준다.
@Service
public class BriefingQueryService {

  public static final Set<String> GROUPS = Set.of("NOTICE", "UPDATE", "EVENT");
  private static final int PAGE_SIZE = 10;
  private static final int MAX_BRIEFINGS = 300;

  private final OfficialNewsRepository news;
  private final GameBriefingRepository briefings;
  private final BriefingWorker worker;

  public BriefingQueryService(OfficialNewsRepository news, GameBriefingRepository briefings, BriefingWorker worker) {
    this.news = news;
    this.briefings = briefings;
    this.worker = worker;
  }

  // page: 0부터, appId·group·topic: 없으면 조건 없음 (값 검증은 컨트롤러가 먼저 한다)
  public BriefingPage find(int page, Long appId, String group, String topic) {
    List<Card> cards = new ArrayList<>();
    for (GameBriefing briefing : briefings.list(appId, PageRequest.of(0, MAX_BRIEFINGS))) {
      Card card = toCard(briefing, group, topic);
      if (card != null) {
        cards.add(card);
      }
    }
    cards.sort(Comparator.comparing(Card::latestSourceAt).reversed());
    int start = Math.min(cards.size(), page * PAGE_SIZE);
    int end = Math.min(cards.size(), start + PAGE_SIZE);
    return new BriefingPage(cards.subList(start, end), cards.size(), end == cards.size(), worker.status());
  }

  // 조건에 맞는 분석 내용이 없으면 null (분류·소주제를 고르지 않았으면 내용이 없어도 카드를 보여준다)
  private Card toCard(GameBriefing briefing, String group, String topic) {
    List<OfficialNews> sources = news.findTop5ByAppIdOrderByPublishedAtDesc(briefing.getAppId());
    boolean stale = !BriefingWorker.hash(sources).equals(briefing.getSourceHash());
    List<BriefingContent.Item> parsed = stale ? List.of()
        : BriefingContent.parse(briefing.getResultJson(), sources.stream().map(OfficialNews::getGid).collect(Collectors.toSet()));
    List<BriefingContent.Item> items = parsed.stream()
        .filter(item -> group == null || (item.topic() != null && group.equals(BriefingContent.TOPICS.get(item.topic()))))
        .filter(item -> topic == null || topic.equals(item.topic()))
        .toList();
    if (items.isEmpty() && (group != null || topic != null)) {
      return null;
    }
    Set<String> sourceIds = items.stream().flatMap(item -> item.sourceIds().stream()).collect(Collectors.toSet());
    Instant latestSourceAt = sources.stream()
        .filter(source -> sourceIds.contains(source.getGid()))
        .map(OfficialNews::getPublishedAt)
        .max(Instant::compareTo)
        .orElse(Instant.EPOCH);
    return new Card(latestSourceAt, briefing.getAppId(), briefing.getGameName(), briefing.getAnalyzedAt(),
        briefing.getSourceCount(), stale, items, sources);
  }

  // 응답 JSON 이름은 화면이 쓰는 그대로다 (items, total, last, status / 카드의 각 칸)
  public record BriefingPage(List<Card> items, int total, boolean last, Map<String, Object> status) {
  }

  public record Card(Instant latestSourceAt, Long appId, String gameName, Instant analyzedAt, int sourceCount,
                     boolean stale, List<BriefingContent.Item> items, List<OfficialNews> sources) {
  }
}
