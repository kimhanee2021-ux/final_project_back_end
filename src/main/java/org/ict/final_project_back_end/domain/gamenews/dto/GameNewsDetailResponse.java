package org.ict.final_project_back_end.domain.gamenews.dto;

import org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import java.time.Instant;

/** Same metadata as the list plus Korean briefing text; Steam raw HTML/BBCode is not returned. */
public record GameNewsDetailResponse(long newsId, long appId, String gameName, String title,
    GameNewsCategory category, String summary, String aiContent, String sourceUrl, Instant publishedAt,
    Instant sourceUpdatedAt, Instant generatedAt, BriefingGenerationStatus generationStatus, boolean stale) { }
