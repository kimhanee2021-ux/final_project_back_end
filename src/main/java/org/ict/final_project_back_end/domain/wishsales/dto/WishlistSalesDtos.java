package org.ict.final_project_back_end.domain.wishsales.dto;

import java.time.OffsetDateTime;
import java.util.List;

// [추가] 위시리스트 세일 요약 기능의 데이터 형식 모음.
// 이 클래스의 record들은 서비스 계산 결과와 API 응답 JSON 구조를 정의한다.
public final class WishlistSalesDtos {
    private WishlistSalesDtos() {} // 객체를 만들 필요가 없는 모음 클래스라서 생성자를 막아 둠

    // ITAD 가격 이력 한 건 (스팀 기준).
    // at: 가격이 바뀐 시각, cut: 그 시점의 할인율(%)
    public record PricePoint(OffsetDateTime at, int cut) {}

    // 게임 한 개의 세일 요약 (계산 결과).
    // status: OK면 요약이 유효, INSUFFICIENT면 데이터가 부족해서 요약하지 않음
    public record SaleSummary(
            long appId,
            String status,
            String reason,            // INSUFFICIENT일 때 사용자에게 보여 줄 이유
            long recordMonths,        // 가격 기록 기간 (개월)
            int saleCount,            // 세일이 새로 시작된 횟수
            int avgCut,               // 세일 중 기록들의 평균 할인율
            int maxCut,               // 기록 중 가장 높은 할인율
            Long lastSaleMonthsAgo    // 마지막 세일이 몇 개월 전인지 (세일이 없으면 null)
    ) {
        // 데이터가 부족해서 요약을 만들 수 없을 때 쓰는 팩토리 메서드.
        // 숫자는 전부 0으로 두고, 이유 문장만 채운다.
        public static SaleSummary insufficient(long appId, String reason) {
            return new SaleSummary(appId, "INSUFFICIENT", reason, 0, 0, 0, 0, null);
        }
    }

    // 게임 한 개의 응답: 계산 결과(summary)와 설명 문장(sentence)을 묶는다.
    // sentence는 AI가 만든 문장, AI가 없으면 백엔드 기본 문장이 들어간다.
    public record GameSalesItem(SaleSummary summary, String sentence) {}

    // API 최상위 응답. 게임 목록을 items에 담는다.
    public record WishlistSalesResponse(List<GameSalesItem> items) {}
}
