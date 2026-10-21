package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightOverview;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachInsightResponse;

// AI 분석 화면. 내 소비 분석 데이터로 숫자를 계산하고, 고른 주제(태그)에 맞춰 AI가 판단·이유·비교·다음 행동을 쓴다.
public interface CoachInsightService {

  // 소비 유형과 지금 고를 수 있는 주제들. AI를 부르지 않는다
  CoachInsightOverview overview(Long userId);

  // topicKey: spending(소비 패턴) · library(라이브러리) · value(가성비) · activity(최근 활동) · budget(예산·환불)
  CoachInsightResponse insight(Long userId, String topicKey);
}
