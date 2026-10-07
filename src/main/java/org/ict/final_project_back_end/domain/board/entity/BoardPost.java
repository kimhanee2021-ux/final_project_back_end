package org.ict.final_project_back_end.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.time.LocalDateTime;

// 게시판 본문(가성비랭킹/세일공유/방치게임인증/구매고민QNA/베타/공지)
@Entity
@Table(name = "board_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoardPost {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private String category;

  // 특정 게임 관련 글일 때만 채워진다
  @Column(name = "app_id")
  private Long appId;

  @Column(length = 50)
  private String tag;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  // 대표 이미지. 여러 장은 BoardPostImage에 둔다.
  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Builder.Default
  @Column(name = "is_anonymous", nullable = false, columnDefinition = "TINYINT(1)")
  private Boolean anonymous = false;

  @Builder.Default
  @Column(name = "view_count", nullable = false)
  private Integer viewCount = 0;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  // ddl-auto로 만든 테이블에는 ON UPDATE CURRENT_TIMESTAMP가 없으므로 JPA가 수정 시각을 채운다
  @PreUpdate
  void touchUpdatedAt() {
    this.updatedAt = LocalDateTime.now();
  }

  public void update(String category, Long appId, String tag, String title,
      String content, boolean anonymous, LocalDateTime expiresAt) {
    this.category = category;
    this.appId = appId;
    this.tag = tag;
    this.title = title;
    this.content = content;
    this.anonymous = anonymous;
    this.expiresAt = expiresAt;
  }
}
