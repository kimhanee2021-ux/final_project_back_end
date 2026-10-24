package org.ict.final_project_back_end.domain.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 추천 모델 서버의 오류 응답 { "code", "error" }. code는 분기용 식별자, error는 사람이 읽는 문구다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelError(String code, String error) {

  public static final String NO_KNOWN_GAMES = "NO_KNOWN_GAMES";

  public boolean isNoKnownGames() {
    return NO_KNOWN_GAMES.equals(code);
  }
}
