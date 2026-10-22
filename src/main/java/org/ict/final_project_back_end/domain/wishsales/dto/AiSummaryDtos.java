package org.ict.final_project_back_end.domain.wishsales.dto;

import java.util.List;

// [추가] AI 요약 API 응답 형식.
// source: AI (Claude가 쓴 문장) 또는 RULE (규칙으로 만든 기본 문장). 화면에 이 차이를 표시한다.
// cooldown: 30초 호출 간격 때문에 이번에는 AI를 부르지 않았는지 여부
public final class AiSummaryDtos {
    private AiSummaryDtos() {} // 모음 클래스라 객체를 만들지 않음

    public record AiSummaryItem(long appId, String sentence, String source) {}

    public record AiSummaryResponse(List<AiSummaryItem> items, boolean cooldown, String message) {}
}
