package org.ict.final_project_back_end.domain.wishsales.dto;

import java.time.OffsetDateTime;

// ITAD 가격 이력 한 건 (스팀 기준).
// at: 가격이 바뀐 시각, cut: 그 시점의 할인율(%)
public record PricePoint(OffsetDateTime at, int cut) {}
