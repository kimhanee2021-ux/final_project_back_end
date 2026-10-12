package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardPostConflictException extends BusinessException {
  public BoardPostConflictException() { super(HttpStatus.CONFLICT, "BOARD_POST_HAS_CHILDREN", "댓글 또는 이미지가 연결된 글은 아직 삭제할 수 없습니다."); }
}