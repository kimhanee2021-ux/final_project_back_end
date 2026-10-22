package org.ict.final_project_back_end.domain.aipc.dto;

public record PcGameRequirements(
    long appId,
    String name,
    String minimum,
    String recommended,
    boolean windows,
    String source,
    String checkedAt) {}
