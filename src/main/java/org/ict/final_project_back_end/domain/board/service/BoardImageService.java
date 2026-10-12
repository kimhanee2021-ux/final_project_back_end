package org.ict.final_project_back_end.domain.board.service;
import java.util.List;
import org.ict.final_project_back_end.domain.board.dto.BoardImageResponse;
import org.springframework.web.multipart.MultipartFile;
public interface BoardImageService {
  BoardImageResponse upload(Long userId, Long postId, MultipartFile file);
  List<BoardImageResponse> list(Long postId);
  byte[] read(Long postId, String key);
  BoardImageResponse selectCover(Long userId, Long postId, Long imageId);
  void delete(Long userId, Long postId, Long imageId);
}
