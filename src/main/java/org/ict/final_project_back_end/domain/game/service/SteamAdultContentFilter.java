package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.client.SteamContentDescriptorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 스팀이 표시한 콘텐츠 설명자로 판정한다.
//  3 = 성인 전용 성적 콘텐츠, 4 = 빈번한 노출 또는 성적 콘텐츠
// 설명자는 appId마다 한 번씩 조회해야 해서, 추천 목록과 카테고리 목록이 같은 게임을 두 번 묻지 않도록
// 결과를 잠깐 기억해 둔다. 스팀 호출이 실패한 게임은 판정할 수 없으므로 목록에 그대로 남긴다.
@Slf4j
@Component
public class SteamAdultContentFilter implements AdultContentFilter {

  private static final Set<Integer> ADULT_DESCRIPTOR_IDS = Set.of(3, 4);
  private static final Duration CACHE_TTL = Duration.ofHours(12);
  // 스팀 상점 API의 호출 제한(대략 5분에 200회)에 걸리지 않도록 호출 사이에 둔다.
  private static final long REQUEST_INTERVAL_MILLIS = 300;

  private final SteamContentDescriptorClient client;
  private final Map<Long, Verdict> verdicts = new ConcurrentHashMap<>();

  public SteamAdultContentFilter(SteamContentDescriptorClient client) {
    this.client = client;
  }

  @Override
  public Set<Long> findAdultAppIds(Collection<Long> appIds) {
    Set<Long> adultAppIds = new LinkedHashSet<>();
    for (Long appId : new LinkedHashSet<>(appIds)) {
      Verdict verdict = verdicts.get(appId);
      if (verdict == null || verdict.isExpired()) {
        verdict = judge(appId);
        if (verdict == null) {
          continue;
        }
        verdicts.put(appId, verdict);
      }
      if (verdict.adult()) {
        adultAppIds.add(appId);
      }
    }
    log.info("성인 콘텐츠 판정 완료 (대상 {}건 중 제외 {}건)", appIds.size(), adultAppIds.size());
    return adultAppIds;
  }

  private Verdict judge(Long appId) {
    try {
      Thread.sleep(REQUEST_INTERVAL_MILLIS);
      Set<Integer> descriptorIds = client.fetchDescriptorIds(appId);
      if (descriptorIds == null) {
        return null;
      }
      return new Verdict(descriptorIds.stream().anyMatch(ADULT_DESCRIPTOR_IDS::contains), Instant.now());
    } catch (RestClientException e) {
      log.warn("스팀 콘텐츠 설명자 조회 실패, 목록에 그대로 둠 (appId={})", appId, e);
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private record Verdict(boolean adult, Instant judgedAt) {

    boolean isExpired() {
      return judgedAt.plus(CACHE_TTL).isBefore(Instant.now());
    }
  }
}
