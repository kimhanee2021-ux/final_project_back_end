package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.client.SteamFeaturedClient;
import org.ict.final_project_back_end.domain.game.entity.FeaturedCategoryGame;
import org.ict.final_project_back_end.domain.game.entity.FeaturedGame;
import org.ict.final_project_back_end.domain.game.repository.FeaturedCategoryGameRepository;
import org.ict.final_project_back_end.domain.game.repository.FeaturedGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

// 스팀 홈 추천(/api/featured의 featured_win 섹션)과 카테고리별 목록(/api/featuredcategories)을
// 매일 한 번 통째로 받아 featured_games / featured_category_games 테이블을 비우고 다시 채우는 스냅샷 캐시.
// 두 응답 모두 "지금 이 시각의 전체 목록"이라 appId 단위로 합치는 의미가 없어
// appId별로 갱신 대상을 고르는 대신, 매번 전체를 교체한다. 스팀 호출이 실패하면 기존 캐시를 그대로 두고
// 다음 스케줄에 다시 시도한다(예외를 던지지 않음).
// 저장 전에 성인·노출 콘텐츠로 표시된 게임은 AdultContentFilter로 걸러낸다.
@Slf4j
@Service
public class FeaturedGameSyncService {

  // featuredcategories 응답 중 게임 목록이 아닌 블록은 이름으로 걸러낸다(장르 요약, 트레일러 슬라이드쇼).
  // "0".."7" 같은 숫자 키는 isExcludedCategoryKey()에서 따로 걸러낸다.
  private static final Set<String> EXCLUDED_CATEGORY_KEYS = Set.of("genres", "trailerslideshow");

  private final SteamFeaturedClient client;
  private final FeaturedGameRepository featuredGameRepository;
  private final FeaturedCategoryGameRepository featuredCategoryGameRepository;
  private final AdultContentFilter adultContentFilter;

  public FeaturedGameSyncService(SteamFeaturedClient client, FeaturedGameRepository featuredGameRepository,
      FeaturedCategoryGameRepository featuredCategoryGameRepository, AdultContentFilter adultContentFilter) {
    this.client = client;
    this.featuredGameRepository = featuredGameRepository;
    this.featuredCategoryGameRepository = featuredCategoryGameRepository;
    this.adultContentFilter = adultContentFilter;
  }

  // 매일 새벽 4:10(KST)에 두 캐시를 갱신한다. 시각은 app.featured.sync-cron으로 바꿀 수 있다.
  @Scheduled(cron = "${app.featured.sync-cron:0 10 4 * * *}", zone = "Asia/Seoul")
  public void refreshAll() {
    refreshFeatured();
    refreshFeaturedCategories();
  }

  public void refreshFeatured() {
    Map<String, Object> body;
    try {
      body = client.fetchFeatured();
    } catch (RestClientException e) {
      log.warn("스팀 featured 조회 실패, 기존 캐시 유지", e);
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    List<FeaturedGame> rows = new ArrayList<>();
    int rank = 0;
    for (Object raw : SteamFeaturedClient.asItemList(body.get("featured_win"))) {
      SteamFeaturedClient.Item item = SteamFeaturedClient.toItem(raw);
      if (item == null) {
        continue;
      }
      rows.add(FeaturedGame.builder()
          .appId(item.appId())
          .rankOrder(rank++)
          .name(item.name())
          .discounted(item.discounted())
          .discountPercent(item.discountPercent())
          .originalPrice(item.originalPrice())
          .finalPrice(item.finalPrice())
          .currency(item.currency())
          .headerImage(item.headerImage())
          .fetchedAt(now)
          .build());
    }

    removeAdultGames(rows, FeaturedGame::getAppId);

    // 전체 스냅샷 교체. delete와 insert 사이 짧은 순간 테이블이 비지만 새벽 시간대 배치라 허용한다.
    featuredGameRepository.deleteAllInBatch();
    featuredGameRepository.saveAll(rows);
    log.info("featured_games 갱신 완료 ({}건)", rows.size());
  }

  public void refreshFeaturedCategories() {
    Map<String, Object> body;
    try {
      body = client.fetchFeaturedCategories();
    } catch (RestClientException e) {
      log.warn("스팀 featuredcategories 조회 실패, 기존 캐시 유지", e);
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    List<FeaturedCategoryGame> rows = new ArrayList<>();
    int categoryOrder = 0;
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      // "status" 같은 비카테고리 필드나 items가 없는 블록, 게임 목록이 아닌 "0".."7"(장르별 서브 블록)/genres/trailerslideshow는 건너뛴다.
      if (isExcludedCategoryKey(entry.getKey())
          || !(entry.getValue() instanceof Map<?, ?> categoryMap)
          || !(categoryMap.get("items") instanceof List<?>)) {
        continue;
      }
      String categoryName = asString(categoryMap.get("name"));
      int order = categoryOrder++;
      int rank = 0;
      for (Object raw : SteamFeaturedClient.asItemList(categoryMap.get("items"))) {
        SteamFeaturedClient.Item item = SteamFeaturedClient.toItem(raw);
        if (item == null) {
          continue;
        }
        rows.add(FeaturedCategoryGame.builder()
            .categoryKey(entry.getKey())
            .categoryName(categoryName != null ? categoryName : entry.getKey())
            .categoryOrder(order)
            .appId(item.appId())
            .rankOrder(rank++)
            .name(item.name())
            .discounted(item.discounted())
            .discountPercent(item.discountPercent())
            .originalPrice(item.originalPrice())
            .finalPrice(item.finalPrice())
            .currency(item.currency())
            .headerImage(item.headerImage())
            .fetchedAt(now)
            .build());
      }
    }

    removeAdultGames(rows, FeaturedCategoryGame::getAppId);

    featuredCategoryGameRepository.deleteAllInBatch();
    featuredCategoryGameRepository.saveAll(rows);
    log.info("featured_category_games 갱신 완료 ({}건)", rows.size());
  }

  // 성인·노출 콘텐츠로 판정된 게임은 저장하기 전에 목록에서 뺀다.
  private <T> void removeAdultGames(List<T> rows, ToLongFunction<T> appIdOf) {
    Set<Long> adultAppIds = adultContentFilter.findAdultAppIds(
        rows.stream().map(row -> appIdOf.applyAsLong(row)).toList());
    rows.removeIf(row -> adultAppIds.contains(appIdOf.applyAsLong(row)));
  }

  // "0".."7" 같은 숫자 키(장르 ID별 서브 블록)와 genres/trailerslideshow 블록은 게임 목록이 아니라 건너뛴다.
  private static boolean isExcludedCategoryKey(String key) {
    return EXCLUDED_CATEGORY_KEYS.contains(key) || key.chars().allMatch(Character::isDigit);
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }
}
