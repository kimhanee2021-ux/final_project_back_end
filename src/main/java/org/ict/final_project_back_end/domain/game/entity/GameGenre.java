package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장르 다중값(미확정, 개인화 추천 지원용). (app_id, genre) 복합 PK.
@Entity
@Table(name = "game_genres")
@IdClass(GameGenreId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameGenre {

  @Id
  @Column(name = "app_id")
  private Long appId;

  @Id
  @Column(length = 50)
  private String genre;
}
