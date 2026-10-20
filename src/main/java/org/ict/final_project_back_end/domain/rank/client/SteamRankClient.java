package org.ict.final_project_back_end.domain.rank.client;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 주간 국가별 원본의 접근 방법/응답 스키마가 검증되기 전까지 네트워크 호출을 구현하지 않는다.
@Component
public class SteamRankClient {

  // 반환 모델도 원본 검증 후 정의한다. 빈 목록이나 임의의 순위를 성공 응답으로 반환하지 않는다.
  public void fetchWeeklyRank(String countryCode, LocalDate weekStart) {
    throw new UnsupportedOperationException("Steam 주간 순위 원본 접근이 검증되지 않아 수집 기능은 미구현입니다.");
  }
}
