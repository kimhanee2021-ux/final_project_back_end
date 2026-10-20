package org.ict.final_project_back_end.domain.rank.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

// 수집/저장 규칙 검증 전에는 스케줄러, 시작 시 실행, 저장/삭제 동작을 연결하지 않는다.
@Service
public class RankSyncService {

  public void syncWeeklyRank(String countryCode, LocalDate weekStart) {
    throw new UnsupportedOperationException("Steam 주간 순위 수집 및 검증이 구현되기 전에는 동기화할 수 없습니다.");
  }
}
