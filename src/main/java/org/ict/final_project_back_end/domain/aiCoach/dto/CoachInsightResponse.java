package org.ict.final_project_back_end.domain.aiCoach.dto;

import java.util.List;

// 한 분석 주제에 대한 AI 결과 카드. 문장 속 숫자는 모두 서버가 facts 값으로 채운 것이다.
// verdict: 판단(가장 중요한 한 가지 결론), reason: 이유(왜 그런지 추정), comparison: 비교(내 다른 기간·장르와 견준 말, 비교할 값이 없으면 빈 문자열),
// action: 다음 행동 한 가지, games: action에서 권한 내 보유 게임(최대 3개, 없으면 빈 목록)
// evidence: 문장에 쓰인 숫자의 근거 (예: "시간당 비용" = "1,250원"), 화면에서 작은 칩으로 표시
public record CoachInsightResponse(String topic, String verdict, String reason, String comparison, String action,
                                   List<CoachChatResponse.GameCard> games, List<CoachChatResponse.Evidence> evidence) {
}
