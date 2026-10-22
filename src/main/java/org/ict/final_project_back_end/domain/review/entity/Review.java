package org.ict.final_project_back_end.domain.review.entity;

import jakarta.persistence.CheckConstraint;
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

// 게임 리뷰(자체 유저 작성분). 사용자당 게임 1개에 리뷰 1건.
@Entity
@Table(name = "reviews",
    uniqueConstraints = @UniqueConstraint(name = "uq_review_game_author", columnNames = {"app_id", "author_id"}),
    check = @CheckConstraint(name = "chk_review_rating", constraint = "rating BETWEEN 1 AND 5"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  // 1~5
  @Column(nullable = false, columnDefinition = "TINYINT")
  private Integer rating;

  @Column(length = 20)
  private String emotion;

  @Column(nullable = false, length = 2000)
  private String content;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
