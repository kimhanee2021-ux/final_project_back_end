package org.ict.final_project_back_end.domain.game.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// GameGenre의 복합 PK (app_id, genre). 필드명은 GameGenre의 @Id 필드명과 같아야 한다.
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GameGenreId implements Serializable {
  private Long appId;
  private String genre;
}
