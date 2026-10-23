package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.global.exception.InvalidRequestException;

import java.util.Arrays;
import java.util.List;

// AI 분석 화면의 분석 주제(태그). 구매내역이 있어야 계산되는 숫자를 쓰는 주제는 구매내역을 가져온 회원에게만 보인다.
// focus는 AI에게 "이 주제에서는 무엇을 짚어 말할지" 알려 주는 안내다 (facts의 어느 칸을 보면 되는지 포함).
public enum InsightTopic {

  SPENDING("spending", "소비 패턴", true,
      "소비 패턴: 연도별·월별 지출 흐름(comparisons, spendingByYear), 할인 의존도(patterns.discountReliance, discountHabit), "
          + "소비 유형(patterns.profile)을 짚으세요. 지출의 변화가 어떤 습관에서 나왔는지가 핵심입니다."),
  LIBRARY("library", "라이브러리 분석", false,
      "라이브러리 분석: 안 한 게임에 묶인 돈(patterns.unplayed), 장르 편중(spendingByGenre), 플레이 집중도(patterns.playConcentration)를 "
          + "짚으세요. 다음에 해 볼 보유 게임을 추천할 수 있습니다."),
  VALUE("value", "가성비 분석", false,
      "가성비 분석: 장르별 시간당 비용(patterns.bestValueGenre, patterns.worstValueGenre), 가성비 좋은·아쉬운 게임(bestValueGames, worstValueGames)을 "
          + "전체 시간당 비용(totals.hourlyCostKrw)과 비교해 짚으세요."),
  ACTIVITY("activity", "최근 활동", false,
      "최근 활동: 요즘 하고 있는 게임(playingRecently), 플레이가 한 게임에 몰렸는지(patterns.playConcentration)를 짚으세요. "
          + "최근 플레이와 오래 안 한 게임을 비교하고 다음에 해 볼 보유 게임을 추천할 수 있습니다."),
  BUDGET("budget", "예산·환불", true,
      "예산·환불: 이번 달 지출과 예산(thisMonth), 환불할 수 있는 게임(refundableGames: 구매 후 14일 안, 플레이 2시간 미만)을 짚으세요. "
          + "예산이 없으면 예산을 정해 두면 남은 금액을 알려 줄 수 있다고 안내하세요.");

  private final String key;
  private final String label;
  private final boolean requiresPurchaseHistory;
  private final String focus;

  InsightTopic(String key, String label, boolean requiresPurchaseHistory, String focus) {
    this.key = key;
    this.label = label;
    this.requiresPurchaseHistory = requiresPurchaseHistory;
    this.focus = focus;
  }

  public String key() {
    return key;
  }

  public String label() {
    return label;
  }

  public String focus() {
    return focus;
  }

  // 구매내역을 가져온 회원이면 모든 주제, 아니면 구매내역 없이 계산되는 주제만 쓸 수 있다
  public boolean availableFor(boolean hasPurchaseHistory) {
    return hasPurchaseHistory || !requiresPurchaseHistory;
  }

  // 화면에 보여줄 순서(선언 순서)대로, 이 회원이 쓸 수 있는 주제들
  public static List<InsightTopic> availableTopics(boolean hasPurchaseHistory) {
    return Arrays.stream(values()).filter(topic -> topic.availableFor(hasPurchaseHistory)).toList();
  }

  // 주소의 ?topic=spending 같은 값을 주제로 바꾼다. 모르는 값이면 400
  public static InsightTopic from(String key) {
    for (InsightTopic topic : values()) {
      if (topic.key.equals(key)) {
        return topic;
      }
    }
    throw new InvalidRequestException("topic은 spending, library, value, activity, budget 중 하나여야 합니다");
  }
}
