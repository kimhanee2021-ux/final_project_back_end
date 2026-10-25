package org.ict.final_project_back_end.domain.library.service;

import org.ict.final_project_back_end.domain.library.dto.LibraryGameResponse;

import java.util.List;

// 스팀 보유 게임 목록을 가져와 우리 DB에 동기화하는 서비스.
public interface LibraryService {

  // 연동된 스팀 계정의 보유 게임을 전부 조회해 저장(없으면 추가, 있으면 갱신)하고,
  // 저장된 전체 목록을 한 번에 반환한다. 페이징은 하지 않는다.
  List<LibraryGameResponse> syncSteamLibrary(Long userId);
}
