package org.ict.final_project_back_end.domain.game.service;

// 스팀 전체 게임 목록을 200개씩 나눠 가져와 games 테이블에 저장하는 동기화.
public interface GameSyncService {

  // 마지막 완료 후 24시간이 지났거나 아직 끝나지 않은 바퀴가 있으면 이어서 동기화한다.
  // 요청 사이마다 5분 대기하므로 전체 한 바퀴는 수 일이 걸리며, 비동기로 실행된다.
  void syncIfDue();

  // 개발용 초기 데이터: 최신 appId부터 확인해 출시된 게임 limit개의 목록과 상세정보를 저장한다(출시 예정 제외). 비동기로 실행된다.
  void seedFirst(int limit);
}
