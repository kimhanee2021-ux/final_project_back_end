package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.entity.GamePriceKrw;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 스프링/DB 없이 도는 단위 테스트: 스팀 응답 해석과 "다시 받을 게임 고르기" 규칙만 검증한다.
class GamePriceKrwServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

  private static SteamPriceClient.AppPrice priced(int initial, int fin, int discount) {
    return new SteamPriceClient.AppPrice(true, Map.of("price_overview",
        Map.of("currency", "KRW", "initial", initial, "final", fin, "discount_percent", discount)));
  }

  private static GamePriceKrw cachedAt(long appId, LocalDateTime fetchedAt) {
    return GamePriceKrw.builder().appId(appId).status(GamePriceKrw.PRICED).initialKrw(1000).fetchedAt(fetchedAt).build();
  }

  @Test
  void parsesKrwPricesAndDividesByHundred() {
    List<GamePriceKrw> result = GamePriceKrwService.toEntities(List.of(413150L),
        Map.of("413150", priced(1600000, 1120000, 30)), NOW);

    GamePriceKrw price = result.get(0);
    assertEquals(GamePriceKrw.PRICED, price.getStatus());
    assertEquals(16000, price.getInitialKrw());
    assertEquals(11200, price.getFinalKrw());
    assertEquals(30, price.getDiscountPercent());
  }

  @Test
  void emptyArrayDataBecomesNoPriceInsteadOfFailing() {
    // 무료 게임(Dota 2)은 실제로 "data": [] 로 온다
    List<GamePriceKrw> result = GamePriceKrwService.toEntities(List.of(570L),
        Map.of("570", new SteamPriceClient.AppPrice(true, List.of())), NOW);

    assertEquals(GamePriceKrw.NO_PRICE, result.get(0).getStatus());
    assertNull(result.get(0).getInitialKrw());
  }

  @Test
  void failedOrMissingAppsAreStoredAsUnavailableSoTheyAreNotAskedAgain() {
    List<GamePriceKrw> result = GamePriceKrwService.toEntities(List.of(1L, 2L),
        Map.of("1", new SteamPriceClient.AppPrice(false, null)), NOW);

    assertEquals(List.of(GamePriceKrw.UNAVAILABLE, GamePriceKrw.UNAVAILABLE),
        result.stream().map(GamePriceKrw::getStatus).toList());
  }

  @Test
  void nonKrwCurrencyIsNotTreatedAsWonPrice() {
    SteamPriceClient.AppPrice usd = new SteamPriceClient.AppPrice(true, Map.of("price_overview",
        Map.of("currency", "USD", "initial", 1999, "final", 1999, "discount_percent", 0)));

    assertEquals(GamePriceKrw.NO_PRICE,
        GamePriceKrwService.toEntities(List.of(7L), Map.of("7", usd), NOW).get(0).getStatus());
  }

  @Test
  void selectsOnlyMissingOrOlderThanOneDayAndRemovesDuplicates() {
    Map<Long, GamePriceKrw> cached = Map.of(
        1L, cachedAt(1, NOW.minusHours(2)),   // 최근에 받음 -> 건너뜀
        2L, cachedAt(2, NOW.minusDays(2)));   // 오래됨 -> 다시 받음

    List<Long> targets = GamePriceKrwService.selectTargets(List.of(1L, 2L, 3L, 3L), cached, NOW);

    assertEquals(List.of(2L, 3L), targets);
  }

  private static GamePriceKrw noPrice(long appId, Boolean freeChecked) {
    return GamePriceKrw.builder().appId(appId).status(GamePriceKrw.NO_PRICE).freeChecked(freeChecked).fetchedAt(NOW).build();
  }

  @Test
  void onlyUncheckedNoPriceGamesNeedAFreeCheck() {
    GamePriceKrw unchecked = noPrice(578080, null);       // 시드로 들어온 PUBG: 아직 확인 전
    GamePriceKrw checkedPaid = noPrice(261570, true);     // Ori: 확인했더니 무료 아님
    GamePriceKrw priced = cachedAt(413150, NOW);

    assertEquals(List.of(578080L),
        GamePriceKrwService.selectFreeCheckTargets(List.of(unchecked, checkedPaid, priced), 20).stream()
            .map(GamePriceKrw::getAppId).toList());
  }

  @Test
  void freeCheckMarksFreeGamesAsZeroWonAndRemembersPaidOnes() {
    GamePriceKrw pubg = noPrice(578080, null);
    GamePriceKrw ori = noPrice(261570, null);

    pubg.applyFreeCheck(true);
    ori.applyFreeCheck(false);

    assertEquals(GamePriceKrw.FREE, pubg.getStatus());
    assertEquals(0, pubg.getInitialKrw());
    assertEquals(GamePriceKrw.NO_PRICE, ori.getStatus());
    assertFalse(ori.needsFreeCheck());
  }

  @Test
  void dailyRefreshKeepsAnEarlierFreeCheck() {
    GamePriceKrw previousFree = noPrice(578080, null);
    previousFree.applyFreeCheck(true);
    GamePriceKrw fresh = noPrice(578080, null); // 묶음 조회는 무료 여부를 모른다

    GamePriceKrw merged = GamePriceKrwService.carryOverFreeCheck(fresh, previousFree);

    assertEquals(GamePriceKrw.FREE, merged.getStatus());
    assertFalse(merged.needsFreeCheck());
  }
}
