package org.ict.final_project_back_end.domain.gamenews.client.catalog;

import org.ict.final_project_back_end.domain.game.service.GameNameResolver.Candidate;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;

import java.util.List;

/** 게임 검색어 확장과 후보 판단을 위한 공통 계약입니다. */
public interface GameCatalogAiClient {
  // selectedIndex는 1부터 시작하며, ambiguous가 true이면 서비스에서 추가 확인이 필요합니다.
  record Decision(Integer selectedIndex, boolean ambiguous) {}

  // 원래 게임명과 시리즈 구분을 보존한 검색어 후보를 반환합니다.
  List<String> expand(String query, AiContext context);

  // 제공된 후보 안에서만 판단하며, 실제 선택과 되묻기는 서비스가 처리합니다.
  Decision choose(String transcript, AiContext context, List<Candidate> candidates);
}
