package org.ict.final_project_back_end.domain.aiPc.dto;

public record PcComponentAdvice(
    String category, String status, String reason, String compatibilityNote, PcPart.Offer offer) {}
