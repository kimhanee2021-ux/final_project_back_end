package org.ict.final_project_back_end.domain.gamenews.dto;

import org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import java.time.Instant;

/** Public list item. Internal hashes, errors, retry state and model configuration are not exposed. */
public record GameNewsSummaryResponse(long newsId, long appId, String gameName, String title,
    GameNewsCategory category, String summary, String sourceUrl, Instant publishedAt,
    Instant sourceUpdatedAt, Instant generatedAt, BriefingGenerationStatus generationStatus, boolean stale) { }
