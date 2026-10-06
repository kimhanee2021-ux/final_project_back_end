package org.ict.final_project_back_end.domain.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.time.LocalDateTime;

// 사용자가 스팀에서 보유한 게임 한 건. 같은 사용자가 같은 게임(appId)을 두 번 저장하지 않도록
// (user_id, app_id) 조합에 유니크 제약을 건다.
@Entity
@Table(name = "library_games", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "app_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LibraryGame {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // 스팀 게임(앱) 고유 번호
  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(nullable = false)
  private String name;

  // 전체 플레이 시간(분 단위, 스팀 원본 값 그대로)
  @Column(name = "playtime_forever", nullable = false)
  private Integer playtimeForever;

  // 최근 2주 플레이 시간(분 단위)
  @Column(name = "playtime_2weeks", nullable = false)
  private Integer playtime2Weeks;

  @Column(name = "last_played_at")
  private LocalDateTime lastPlayedAt;

  @Column(name = "icon_url", length = 500)
  private String iconUrl;

  // 동기화 시 스팀에서 받은 최신 값으로 덮어쓴다 (appId/user는 바뀌지 않는 식별 정보라 제외)
  public void updateFromSteam(String name, Integer playtimeForever, Integer playtime2Weeks,
                              LocalDateTime lastPlayedAt, String iconUrl) {
    this.name = name;
    this.playtimeForever = playtimeForever;
    this.playtime2Weeks = playtime2Weeks;
    this.lastPlayedAt = lastPlayedAt;
    this.iconUrl = iconUrl;
  }
}
