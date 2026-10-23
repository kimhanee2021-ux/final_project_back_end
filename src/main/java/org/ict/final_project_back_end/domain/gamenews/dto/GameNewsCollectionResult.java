package org.ict.final_project_back_end.domain.gamenews.dto;

/** stale means a later-started collection was already applied, so this response was ignored. */
public record GameNewsCollectionResult(long appId, int received, int inserted, int updated,
                                       int unchanged, boolean stale) { }
