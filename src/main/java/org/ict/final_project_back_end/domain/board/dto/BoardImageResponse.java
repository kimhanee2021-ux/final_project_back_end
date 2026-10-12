package org.ict.final_project_back_end.domain.board.dto;
import org.ict.final_project_back_end.domain.board.entity.BoardPostImage;
public record BoardImageResponse(Long id, String url, int sortOrder) {
  public static BoardImageResponse from(BoardPostImage image) {
    return new BoardImageResponse(image.getId(), image.getImageUrl(), image.getSortOrder());
  }
}
