package org.ict.final_project_back_end.domain.notice.service;

import org.ict.final_project_back_end.domain.notice.dto.*;

public interface NoticeService {
  NoticeDetailResponse create(Long userId, NoticeRequest request);
  NoticeDetailResponse find(Long id);
  NoticePageResponse list(int page, int size);
  NoticeDetailResponse update(Long userId, Long id, NoticeRequest request);
  void delete(Long userId, Long id);
}
