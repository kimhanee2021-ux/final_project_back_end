package org.ict.final_project_back_end.domain.analysis.service;

import org.ict.final_project_back_end.domain.purchase.entity.PurchaseItem;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 가져온 구매내역(원화 환산 금액)을 게임별 지출로 모은다. 규칙:
//   - 상품이 모두 같은 게임(DLC는 본편)으로 연결된 거래는 그 게임에 넣는다
//   - 서로 다른 게임이 섞인 번들은 각 게임의 현재 한국 정가 비율로 결제액을 나눠 넣는다(정가를 모르면 똑같이)
//     -> 배분한 금액임을 bundleCount로 표시한다
//   - 게임을 못 찾은 상품이 섞인 거래와 선물 구매는 "게임별로 나눌 수 없는 지출"(unallocated)로 모은다
//   - 환불은 같은 groupKey의 구매를 찾아 빼고(번들이면 같은 비율로), 금액 칸이 비어 있으면 그 구매의 전액을 뺀다
//   - 원화로 바꾸지 못한 거래(지원하지 않는 통화)는 금액 집계에서 빼고 개수만 센다
final class PurchaseAggregator {

  record GameSpend(int paidKrw, int refundKrw, LocalDate firstPurchasedAt, int dlcCount, int savedKrw, int bundleCount) {
    static final GameSpend EMPTY = new GameSpend(0, 0, null, 0, 0, 0);

    GameSpend add(GameSpend other) {
      LocalDate first = firstPurchasedAt == null
          || (other.firstPurchasedAt != null && other.firstPurchasedAt.isBefore(firstPurchasedAt))
          ? other.firstPurchasedAt : firstPurchasedAt;
      return new GameSpend(paidKrw + other.paidKrw, refundKrw + other.refundKrw, first, dlcCount + other.dlcCount,
          savedKrw + other.savedKrw, bundleCount + other.bundleCount);
    }
  }

  // 월별 차트용 거래 한 건. amountKrw는 환불이면 음수, appId는 연결된 게임(한 게임이 아니면 null)
  record TransactionView(LocalDate purchasedAt, String type, int amountKrw, Long appId, List<String> items, int savedKrw) {
  }

  record Result(Map<Long, GameSpend> byGame, int unallocatedKrw, int savedKrw, int unconvertedCount,
                int unmatchedItemCount, List<TransactionView> transactions) {
  }

  private PurchaseAggregator() {
  }

  static Result aggregate(List<PurchaseTransaction> transactions) {
    return aggregate(transactions, Map.of());
  }

  // listPrices: appId -> 현재 한국 정가(원). 번들 배분 비율에만 쓴다
  static Result aggregate(List<PurchaseTransaction> transactions, Map<Long, Integer> listPrices) {
    Map<String, PurchaseTransaction> purchaseByGroup = new HashMap<>();
    for (PurchaseTransaction tx : transactions) {
      if (!PurchaseTransaction.REFUND.equals(tx.getType())) {
        purchaseByGroup.putIfAbsent(tx.getGroupKey(), tx);
      }
    }

    Map<Long, GameSpend> byGame = new HashMap<>();
    List<TransactionView> views = new ArrayList<>();
    int unallocated = 0;
    int saved = 0;
    int unconverted = 0;
    int unmatchedItems = 0;
    for (PurchaseTransaction tx : transactions) {
      unmatchedItems += (int) tx.getItems().stream().filter(i -> PurchaseItem.UNMATCHED.equals(i.getMatchStatus())).count();
      boolean refund = PurchaseTransaction.REFUND.equals(tx.getType());
      Integer krw = refund ? refundKrw(tx, purchaseByGroup.get(tx.getGroupKey())) : tx.getAmountKrw();
      if (krw == null) {
        unconverted++;
        continue;
      }
      int savedKrw = refund ? 0 : savedKrw(tx);
      List<Long> targets = PurchaseTransaction.GIFT.equals(tx.getType()) ? List.of() : targets(tx);
      if (targets.isEmpty()) {
        unallocated += refund ? -krw : krw;
      } else {
        boolean bundle = targets.size() > 1;
        Map<Long, Integer> amounts = split(krw, targets, listPrices);
        Map<Long, Integer> savings = split(savedKrw, targets, listPrices);
        for (Long target : targets) {
          int dlc = refund ? 0 : (int) tx.getItems().stream()
              .filter(i -> PurchaseItem.DLC.equals(i.getMatchStatus()) && target.equals(i.targetAppId())).count();
          byGame.merge(target, new GameSpend(refund ? 0 : amounts.get(target), refund ? amounts.get(target) : 0,
              refund ? null : tx.getPurchasedAt(), dlc, savings.get(target), bundle && !refund ? 1 : 0), GameSpend::add);
        }
      }
      saved += savedKrw;
      views.add(new TransactionView(tx.getPurchasedAt(), tx.getType(), refund ? -krw : krw,
          targets.size() == 1 ? targets.get(0) : null, tx.getItems().stream().map(PurchaseItem::getRawName).toList(),
          savedKrw));
    }
    return new Result(byGame, unallocated, saved, unconverted, unmatchedItems, views);
  }

  // 거래에 담긴 상품들이 연결된 게임 목록(DLC는 본편, 중복 제거). 연결 못 한 상품이 하나라도 있으면 빈 목록.
  private static List<Long> targets(PurchaseTransaction tx) {
    Set<Long> targets = new LinkedHashSet<>();
    for (PurchaseItem item : tx.getItems()) {
      Long id = item.targetAppId();
      if (id == null) {
        return List.of();
      }
      targets.add(id);
    }
    return List.copyOf(targets);
  }

  // 금액을 게임들에 나눈다. 모든 게임의 정가를 알면 정가 비율로, 하나라도 모르면 똑같이 나눈다.
  // 원 단위로 내림한 뒤 남는 원은 비중이 큰 게임부터 1원씩 더해 합계가 정확히 amount가 되게 한다.
  static Map<Long, Integer> split(int amount, List<Long> targets, Map<Long, Integer> listPrices) {
    Map<Long, Integer> result = new LinkedHashMap<>();
    if (targets.size() == 1) {
      result.put(targets.get(0), amount);
      return result;
    }
    boolean byPrice = targets.stream().allMatch(id -> listPrices.getOrDefault(id, 0) > 0);
    long totalWeight = byPrice ? targets.stream().mapToLong(listPrices::get).sum() : targets.size();
    int assigned = 0;
    for (Long id : targets) {
      long weight = byPrice ? listPrices.get(id) : 1;
      int share = (int) (amount * weight / totalWeight);
      result.put(id, share);
      assigned += share;
    }
    List<Long> byWeight = new ArrayList<>(targets);
    byWeight.sort((a, b) -> Long.compare(byPrice ? listPrices.get(b) : 0, byPrice ? listPrices.get(a) : 0));
    for (int i = 0; assigned < amount; i = (i + 1) % byWeight.size(), assigned++) {
      result.merge(byWeight.get(i), 1, Integer::sum);
    }
    return result;
  }

  // 환불 금액이 있으면 그 금액, 비어 있으면(원래 결제 수단으로 전액 환불) 같은 거래의 구매 금액
  private static Integer refundKrw(PurchaseTransaction refund, PurchaseTransaction purchase) {
    if (refund.getAmountKrw() != null) {
      return refund.getAmountKrw();
    }
    return refund.getAmount() == null && purchase != null ? purchase.getAmountKrw() : null;
  }

  // 할인으로 아낀 금액 = (원래 가격 - 낸 금액) x 그날 환율
  private static int savedKrw(PurchaseTransaction tx) {
    if (tx.getOriginalAmount() == null || tx.getAmount() == null || tx.getExchangeRate() == null) {
      return 0;
    }
    BigDecimal diff = tx.getOriginalAmount().subtract(tx.getAmount());
    return diff.signum() <= 0 ? 0 : diff.multiply(tx.getExchangeRate()).setScale(0, RoundingMode.HALF_UP).intValue();
  }
}
