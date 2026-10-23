package org.ict.final_project_back_end.domain.aipc.dto;

/** AI의 목표 프레임 구간 추정. 현재 PC 실측이나 최소프레임 보장이 아니다. */
public record PcTarget(
    String grade,
    String label,
    String preset,
    String confidence,
    String reasonCode,
    String reason,
    String method) {}
