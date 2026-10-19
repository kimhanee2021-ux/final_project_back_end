package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse.PurchaseView;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

// "근거 보기"에 영어 칸 이름(discountedSpendPercent 같은 것)이 그대로 나오지 않는지, 코치가 가리킬 수 있는 모든 숫자 칸으로 검증한다
class CoachEvidenceLabelsTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

  private static SpendingGameResponse game(long appId, String name, String genre, Integer amount, String source,
                                           double hours, LocalDate purchasedAt, int refund, int saved) {
    return new SpendingGameResponse(appId, name, genre, genre == null ? List.of() : List.of(genre), null, amount, source,
        null, null, hours, 0, null, null, refund, purchasedAt, 0, saved, false, null, null);
  }

  @Test
  void everyNumericFieldTheCoachCanPointAtHasAKoreanLabel() {
    List<SpendingGameResponse> games = List.of(
        game(1, "Dead Cells", "Action", 20000, "CONFIRMED", 40, LocalDate.of(2025, 3, 1), 0, 10000),
        game(2, "Ori 2", "Adventure", 5000, "CONFIRMED", 0, LocalDate.of(2026, 10, 10), 0, 4000),
        game(3, "Witcher 3", "RPG", 30000, "CONFIRMED", 2, LocalDate.of(2026, 1, 5), 10000, 0),
        game(4, "Aimlabs", "Casual", 0, "FREE", 5, null, 0, 0));
    List<PurchaseView> transactions = List.of(
        new PurchaseView(LocalDate.of(2026, 10, 10), "PURCHASE", 5000, 2L, List.of("Ori 2"), 4000),
        new PurchaseView(LocalDate.of(2026, 6, 5), "PURCHASE", 20000, 1L, List.of("Dead Cells"), 10000),
        new PurchaseView(LocalDate.of(2025, 3, 1), "PURCHASE", 30000, 3L, List.of("Witcher 3"), 0));
    SpendingAnalysisResponse spending = new SpendingAnalysisResponse(TODAY, "PURCHASE_HISTORY", games, true, 3000, 5000, 0, 0, transactions);
    Map<String, Object> facts = CoachFactsBuilder.build(spending, new BudgetResponse("2026-10", 100000), TODAY);

    List<String> unlabeled = new ArrayList<>();
    walk(facts, facts, "", unlabeled);
    assertTrue(unlabeled.isEmpty(), "한글 이름이 없는 칸: " + unlabeled);
  }

  private static void walk(Map<String, Object> facts, Object node, String path, List<String> unlabeled) {
    if (node instanceof Map<?, ?> map) {
      map.forEach((key, value) -> walk(facts, value, path.isEmpty() ? String.valueOf(key) : path + "." + key, unlabeled));
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        walk(facts, list.get(i), path + "." + i, unlabeled);
      }
    } else if (node instanceof Number && !path.endsWith(".appId")) { // appId는 근거 보기에 안 나오는 내부 번호
      String label = CoachAnswerRenderer.describe(facts, path);
      for (String part : path.split("\\.")) {
        // 영어 칸 이름이 라벨에 그대로 남았다면 한글 이름표가 없다는 뜻 (숫자 키와 게임 이름은 해당 없음)
        if (part.matches("[A-Za-z][A-Za-z0-9]*") && label.contains(part)) {
          unlabeled.add(path + " -> " + label);
          return;
        }
      }
    }
  }
}
