package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsDetailResponse;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsPageResponse;

public interface GameNewsQueryService {
  GameNewsPageResponse list(Long appId, String category, int page, int size);
  GameNewsDetailResponse detail(long appId, long newsId);
}
