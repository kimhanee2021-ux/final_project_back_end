package org.ict.final_project_back_end.domain.board.service;

import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostPageResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostUpdateRequest;

public interface BoardPostService {
  BoardPostCreateResponse createPost(Long authenticatedUserId, BoardPostCreateRequest request);
  BoardPostDetailResponse findPost(Long postId);
  BoardPostPageResponse listPosts(int page, int size, String category, String keyword);

  BoardPostDetailResponse updatePost(Long userId, Long postId,
      BoardPostUpdateRequest request);
  void deletePost(Long userId, Long postId);
}
