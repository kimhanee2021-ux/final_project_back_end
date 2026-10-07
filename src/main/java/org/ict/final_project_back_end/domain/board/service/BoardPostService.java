package org.ict.final_project_back_end.domain.board.service;

import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;

public interface BoardPostService {
  BoardPostCreateResponse createPost(Long authenticatedUserId, BoardPostCreateRequest request);
  BoardPostDetailResponse findPost(Long postId);
}
