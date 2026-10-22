package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatResponse;

// AI 게임 소비 코치. 내 소비 분석 데이터로 숫자를 계산하고, AI가 질문에 맞춰 설명·조언·보유 게임 추천을 한다.
public interface CoachService {

  CoachChatResponse chat(Long userId, CoachChatRequest request);
}
