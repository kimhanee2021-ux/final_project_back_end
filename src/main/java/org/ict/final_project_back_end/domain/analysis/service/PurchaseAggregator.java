package org.ict.final_project_back_end.domain.analysis.service;

import org.ict.final_project_back_end.domain.purchase.entity.PurchaseItem;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 가져온 구매내역(원화 환산 금액)을 게임별 지출로 모은다. 규칙:
//   - 상품이 모두 같은 게임(DLC는 본편)으로 연결된 거래만 그 게임에 넣는다
//   - 서로 다른 게임이 섞인 번들, 게임을 못 찾은 상품, 선물 구매는 "게임별로 나눌 수 없는 지출"(unallocated)로 모은다
//   - 환불은 같은 groupKey의 구매를 찾아 빼고, 금액 칸이 비어 있으면 그 구매의 전액을 뺀다
//   - 원화로 바꾸지 못한 거래(지원하지 않는 통화)는 금액 집계에서 빼고 개수만 센다
final class PurchaseAggregator {

  record GameSpend(int paidKrw, int refundKrw, LocalDate firstPurchasedAt, int dlcCount, int savedKrw) {
    GameSpend add(int paid, int refund, LocalDate date, int dlc, int saved) {
      LocalDate first = firstPurchasedAt == null || (date != null && date.isBefore(firstPurchasedAt)) ? date : firstPurchasedAt;
      return new GameSpend(paidKrw + paid, refundKrw + refund, first, dlcCount + dlc, savedKrw + saved);
    }
  }

  // 월별 차트용 거래 한 건. amountKrw는 환불이면 음수, appId는 연결된 게임(없으면 null)
  record TransactionView(LocalDate purchasedAt, String type, int amountKrw, Long appId, List<String> items, int savedKrw) {
  }

  record Result(Map<Long, GameSpend> byGame, int unallocatedKrw, int savedKrw, int unconvertedCount,
                int unmatchedItemCount, List<TransactionView> transactions) {
  }

  private PurchaseAggregator() {
  }

  static Result aggregate(List<PurchaseTransaction> transactions) {
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
      Long target = PurchaseTransaction.GIFT.equals(tx.getType()) ? null : singleTarget(tx);
      if (target != null) {
        int dlc = refund ? 0 : (int) tx.getItems().stream().filter(i -> PurchaseItem.DLC.equals(i.getMatchStatus())).count();
        byGame.merge(target,
            new GameSpend(0, 0, null, 0, 0).add(refund ? 0 : krw, refund ? krw : 0, refund ? null : tx.getPurchasedAt(), dlc, savedKrw),
            (a, b) -> a.add(b.paidKrw(), b.refundKrw(), b.firstPurchasedAt(), b.dlcCount(), b.savedKrw()));
      } else {
        unallocated += refund ? -krw : krw;
      }
      saved += savedKrw;
      views.add(new TransactionView(tx.getPurchasedAt(), tx.getType(), refund ? -krw : krw, target,
          tx.getItems().stream().map(PurchaseItem::getRawName).toList(), savedKrw));
    }
    return new Result(byGame, unallocated, saved, unconverted, unmatchedItems, views);
  }

  // 상품이 모두 같은 게임(DLC는 본편)으로 연결돼 있으면 그 게임, 아니면 null
  private static Long singleTarget(PurchaseTransaction tx) {
    Long target = null;
    for (PurchaseItem item : tx.getItems()) {
      Long id = item.targetAppId();
      if (id == null || (target != null && !Objects.equals(target, id))) {
        return null;
      }
      target = id;
    }
    return target;
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
