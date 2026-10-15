package org.ict.final_project_back_end.domain.analysis.service;

import org.ict.final_project_back_end.domain.purchase.entity.PurchaseItem;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseAggregatorTest {

  private static PurchaseItem item(String name, Long appId, Long baseAppId, String status) {
    return PurchaseItem.builder().rawName(name).appId(appId).baseAppId(baseAppId).matchStatus(status).build();
  }

  private static PurchaseTransaction tx(String group, String type, String date, Integer krw, BigDecimal amount,
                                        PurchaseItem... items) {
    PurchaseTransaction transaction = PurchaseTransaction.builder().externalKey(group + type).groupKey(group).type(type)
        .purchasedAt(LocalDate.parse(date)).amountKrw(krw).amount(amount).currency("KRW").exchangeRate(BigDecimal.ONE).build();
    for (PurchaseItem i : items) {
      transaction.addItem(i);
    }
    return transaction;
  }

  @Test
  void dlcBundleGoesToTheBaseGameAndCountsDlcs() {
    PurchaseTransaction base = tx("g1", PurchaseTransaction.PURCHASE, "2024-01-05", 20000, new BigDecimal("20000"),
        item("Risk of Rain 2", 632360L, null, PurchaseItem.MATCHED));
    PurchaseTransaction dlcs = tx("g2", PurchaseTransaction.PURCHASE, "2025-10-12", 7241, new BigDecimal("7241"),
        item("Risk of Rain 2: Seekers of the Storm", 2306620L, 632360L, PurchaseItem.DLC),
        item("Risk of Rain 2 - Survivors of the Void", 1607100L, 632360L, PurchaseItem.DLC));

    PurchaseAggregator.GameSpend spend = PurchaseAggregator.aggregate(List.of(dlcs, base)).byGame().get(632360L);

    assertEquals(27241, spend.paidKrw());
    assertEquals(2, spend.dlcCount());
    assertEquals(LocalDate.parse("2024-01-05"), spend.firstPurchasedAt());
  }

  @Test
  void refundWithoutAmountRefundsTheWholePurchaseOfTheSameGroup() {
    PurchaseTransaction refund = tx("j", PurchaseTransaction.REFUND, "2020-12-26", null, null, item("Journey", 638230L, null, PurchaseItem.MATCHED));
    PurchaseTransaction purchase = tx("j", PurchaseTransaction.PURCHASE, "2020-12-26", 10070, new BigDecimal("10070"), item("Journey", 638230L, null, PurchaseItem.MATCHED));

    PurchaseAggregator.Result result = PurchaseAggregator.aggregate(List.of(refund, purchase));

    assertEquals(10070, result.byGame().get(638230L).paidKrw());
    assertEquals(10070, result.byGame().get(638230L).refundKrw());
    assertEquals(-10070, result.transactions().get(0).amountKrw());
  }

  @Test
  void unmatchedItemsAndGiftsAreUnallocatedButMatchedBundlesAreSplit() {
    PurchaseTransaction mixed = tx("m", PurchaseTransaction.PURCHASE, "2023-01-01", 30000, new BigDecimal("30000"),
        item("Game A", 1L, null, PurchaseItem.MATCHED), item("Game B", 2L, null, PurchaseItem.MATCHED));
    PurchaseTransaction unknown = tx("u", PurchaseTransaction.PURCHASE, "2023-02-01", 5000, new BigDecimal("5000"),
        item("Mystery", null, null, PurchaseItem.UNMATCHED));
    PurchaseTransaction gift = tx("f", PurchaseTransaction.GIFT, "2023-03-01", 8000, new BigDecimal("8000"),
        item("Game A", 1L, null, PurchaseItem.MATCHED));

    PurchaseAggregator.Result result = PurchaseAggregator.aggregate(List.of(mixed, unknown, gift));

    // 정가를 모르는 번들은 똑같이 나눈다
    assertEquals(15000, result.byGame().get(1L).paidKrw());
    assertEquals(15000, result.byGame().get(2L).paidKrw());
    assertEquals(1, result.byGame().get(2L).bundleCount());
    assertEquals(13000, result.unallocatedKrw());
    assertEquals(1, result.unmatchedItemCount());
  }

  @Test
  void unconvertedCurrenciesAreCountedButNotSummed() {
    PurchaseTransaction twd = tx("t", PurchaseTransaction.PURCHASE, "2023-01-01", null, new BigDecimal("390"),
        item("Game A", 1L, null, PurchaseItem.MATCHED));

    PurchaseAggregator.Result result = PurchaseAggregator.aggregate(List.of(twd));

    assertEquals(1, result.unconvertedCount());
    assertTrue(result.byGame().isEmpty());
  }

  @Test
  void savedAmountUsesTheDiscountAndTheDayRate() {
    PurchaseTransaction discounted = PurchaseTransaction.builder().externalKey("d").groupKey("d")
        .type(PurchaseTransaction.PURCHASE).purchasedAt(LocalDate.parse("2025-10-12"))
        .amount(new BigDecimal("452")).originalAmount(new BigDecimal("698")).currency("INR")
        .exchangeRate(new BigDecimal("16.019")).amountKrw(7241).build();
    discounted.addItem(item("Risk of Rain 2: Seekers of the Storm", 2306620L, 632360L, PurchaseItem.DLC));

    assertEquals(3941, PurchaseAggregator.aggregate(List.of(discounted)).savedKrw()); // (698-452) x 16.019
  }

  @Test
  void bundlesAreSplitByListPriceAndTheSharesAddUpExactly() {
    // 실제 사례: Ori 두 게임 번들 7,364원, 한국 정가 32,000원 : 21,500원
    PurchaseTransaction ori = tx("o", PurchaseTransaction.PURCHASE, "2023-07-06", 7364, new BigDecimal("466"),
        item("Ori and the Will of the Wisps", 1057090L, null, PurchaseItem.MATCHED),
        item("Ori and the Blind Forest: Definitive Edition", 387290L, null, PurchaseItem.MATCHED));

    PurchaseAggregator.Result result = PurchaseAggregator.aggregate(List.of(ori), java.util.Map.of(1057090L, 32000, 387290L, 21500));

    int wisps = result.byGame().get(1057090L).paidKrw();
    int blind = result.byGame().get(387290L).paidKrw();
    assertEquals(7364, wisps + blind);
    assertEquals(4405, wisps);
    assertEquals(0, result.unallocatedKrw());
  }

  @Test
  void splitGivesLeftoverWonToTheBiggestShares() {
    java.util.Map<Long, Integer> shares = PurchaseAggregator.split(100, List.of(1L, 2L, 3L), java.util.Map.of());
    assertEquals(100, shares.values().stream().mapToInt(Integer::intValue).sum());
    assertEquals(List.of(34, 33, 33), List.copyOf(shares.values()));
  }
}
