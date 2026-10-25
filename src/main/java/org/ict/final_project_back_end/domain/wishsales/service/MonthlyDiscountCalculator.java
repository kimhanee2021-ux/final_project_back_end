package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.dto.PricePoint;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;

// [추가] 변동 시점만 있는 가격 이력을 "월별 최대 할인율"로 바꾸는 계산 클래스.
// 규칙: 월 시작 시점의 할인율(직전 기록을 이어받음)과 그 달 안의 기록 중 최대값.
// 학습 때 쓴 build_panel.py와 같은 규칙이라서, 결과가 학습 데이터와 맞아야 한다.
public final class MonthlyDiscountCalculator {
    private MonthlyDiscountCalculator() {} // 유틸리티 클래스

    // points: 시간순 정렬된 가격 이력
    // from ~ to: 계산할 달 범위 (양 끝 포함)
    // 반환값: 달 -> 최대 할인율. 기록 시작 전 달은 값이 없어서 빠진다.
    public static Map<YearMonth, Integer> monthlyMax(java.util.List<PricePoint> points, YearMonth from, YearMonth to) {
        Map<YearMonth, Integer> out = new TreeMap<>();
        if (points == null || points.isEmpty()) return out;

        OffsetDateTime first = points.get(0).at();
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            OffsetDateTime start = ym.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            // 이 달이 끝나기 전에 기록이 시작됐는지 확인 (시작 전 달은 값을 모름)
            if (!first.isBefore(end)) continue;

            int startCut = points.get(0).cut();  // 시작 전 기록이 없으면 첫 기록 값을 쓴다
            int max = Integer.MIN_VALUE;
            for (PricePoint p : points) {
                if (p.at().isBefore(start)) {
                    startCut = p.cut();           // 시작 직전까지의 마지막 값
                } else if (p.at().isBefore(end)) {
                    max = Math.max(max, p.cut()); // 이 달 안의 기록
                }
            }
            out.put(ym, Math.max(max, startCut));
        }
        return out;
    }
}
