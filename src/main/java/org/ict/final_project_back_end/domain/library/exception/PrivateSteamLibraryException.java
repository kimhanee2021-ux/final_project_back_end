package org.ict.final_project_back_end.domain.library.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

// 스팀 게임 목록이 비공개이면 스팀은 games 정보를 내려주지 않는다.
// 이 상태에서 빈 목록으로 저장하면 기존 데이터를 실수로 비우는 것과 같으므로 별도 예외로 막는다.
public class PrivateSteamLibraryException extends BusinessException {

  public PrivateSteamLibraryException() {
    super(HttpStatus.BAD_REQUEST, "PRIVATE_STEAM_LIBRARY", "스팀 게임 목록이 비공개이거나 조회할 수 없습니다. 스팀 프로필의 게임 세부 정보를 공개로 설정해주세요");
  }
}
