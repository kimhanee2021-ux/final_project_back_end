package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardReplyDepthException extends BusinessException {
  public BoardReplyDepthException() { super(HttpStatus.BAD_REQUEST, "BOARD_REPLY_DEPTH_EXCEEDED", "대댓글에는 답글을 달 수 없습니다."); }
}
