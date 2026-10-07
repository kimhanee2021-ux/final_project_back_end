package org.ict.final_project_back_end.domain.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 외부(스팀) 리뷰 중 공감 최다 호평·악평 - 악평호평리뷰
@Entity
@Table(name = "external_reviews",
    uniqueConstraints = @UniqueConstraint(name = "uq_external_review", columnNames = {"source", "external_review_id"}),
    indexes = @Index(name = "idx_external_review", columnList = "app_id, sentiment, helpful_votes"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExternalReview {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  // 예: STEAM
  @Column(nullable = false, length = 30)
  private String source;

  @Column(name = "external_review_id", nullable = false, length = 64)
  private String externalReviewId;

  // POSITIVE / NEGATIVE
  @Column(nullable = false, length = 10)
  private String sentiment;

  @Column(nullable = false, length = 500)
  private String excerpt;

  @Column(name = "summary_ko", length = 500)
  private String summaryKo;

  @Builder.Default
  @Column(name = "helpful_votes", nullable = false)
  private Integer helpfulVotes = 0;

  @Column(name = "author_playtime_minutes")
  private Integer authorPlaytimeMinutes;

  @Column(name = "source_url", nullable = false, length = 500)
  private String sourceUrl;

  @Column(length = 10)
  private String language;

  @Column(name = "posted_at")
  private LocalDateTime postedAt;

  @Builder.Default
  @Column(name = "collected_at", nullable = false, updatable = false)
  private LocalDateTime collectedAt = LocalDateTime.now();
}
