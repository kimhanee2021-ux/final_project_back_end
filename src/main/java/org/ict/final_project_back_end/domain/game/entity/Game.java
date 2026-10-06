package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 스팀에 등록된 전체 게임(앱) 목록의 한 건. appId는 스팀이 부여한 고유 번호라 유니크 제약을 건다.
@Entity
@Table(name = "games", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Game {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 스팀 앱 고유 번호
  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(nullable = false, length = 500)
  private String name;

  // 스팀에서 이름이 바뀌었을 때 재동기화로 덮어쓴다
  public void rename(String name) {
    this.name = name;
  }
}
