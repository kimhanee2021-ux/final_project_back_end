package org.ict.final_project_back_end.domain.board.dto;
public record BoardLikeResponse(Long postId, long likeCount, boolean liked) { }
