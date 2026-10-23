package org.ict.final_project_back_end.domain.gamenews.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.game.entity.Game;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Steam 원문. 시각은 UTC이며, 게임과 Steam 공지 번호는 생성 후 변경하지 않는다. */
@Entity
@Table(name = "game_news",
    uniqueConstraints = @UniqueConstraint(name = "uq_game_news_source", columnNames = {"app_id", "source_news_id"}),
    indexes = @Index(name = "idx_game_news_app_published", columnList = "app_id,published_at,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameNews {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_game_news_game"))
  private Game game;

  // Steam gid는 숫자 연산 대상이 아닌 외부 식별자이므로 문자열로 보존한다.
  @Column(name = "source_news_id", nullable = false, updatable = false, length = 64)
  private String sourceNewsId;

  @Builder.Default
  @Column(name = "feed_name", nullable = false, length = 100)
  private String feedName = "steam_community_announcements";

  @Column(nullable = false, length = 1000)
  private String title;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "source_url", nullable = false, length = 2048)
  private String sourceUrl;

  @Column(name = "published_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime publishedAt;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "collected_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
  private LocalDateTime collectedAt;

  @Column(name = "last_seen_at", nullable = false, columnDefinition = "DATETIME(6)")
  private LocalDateTime lastSeenAt;

  @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
  private LocalDateTime updatedAt;

  public Long getAppId() {
    return game.getAppId();
  }

  @PrePersist
  void initializeTimestamps() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    if (collectedAt == null) collectedAt = now;
    if (lastSeenAt == null) lastSeenAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  /** 수집기가 원문 전체의 해시를 계산해 전달한다. 재확인만으로 변경 시각을 바꾸지 않는다. */
  public boolean refreshSource(String title, String content, String sourceUrl,
      LocalDateTime publishedAt, String contentHash, LocalDateTime checkedAt) {
    Objects.requireNonNull(contentHash, "contentHash");
    Objects.requireNonNull(checkedAt, "checkedAt");
    this.lastSeenAt = checkedAt;
    if (Objects.equals(this.contentHash, contentHash)) return false;
    this.title = Objects.requireNonNull(title, "title");
    this.content = Objects.requireNonNull(content, "content");
    this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl");
    this.publishedAt = publishedAt;
    this.contentHash = contentHash;
    this.updatedAt = checkedAt;
    return true;
  }
}
