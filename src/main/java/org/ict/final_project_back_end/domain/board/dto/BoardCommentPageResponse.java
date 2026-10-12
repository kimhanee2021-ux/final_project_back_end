package org.ict.final_project_back_end.domain.board.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record BoardCommentPageResponse(List<BoardCommentResponse> content, int page, int size,
    long totalElements, int totalPages, boolean first, boolean last) {
  public static BoardCommentPageResponse from(Page<BoardCommentResponse> page) {
    return new BoardCommentPageResponse(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
  }
}
