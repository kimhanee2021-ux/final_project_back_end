package org.ict.final_project_back_end.domain.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게임뉴스(네이버 API HUB 수집). 같은 기사 중복 수집을 막기 위해 source_url에 유니크.
@Entity
@Table(name = "news_articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class NewsArticle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(name = "source_name", length = 100)
  private String sourceName;

  @Column(name = "source_url", nullable = false, unique = true, length = 500)
  private String sourceUrl;

  @Column(length = 500)
  private String summary;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;

  @Column(length = 20)
  private String category;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Builder.Default
  @Column(name = "collected_at", nullable = false, updatable = false)
  private LocalDateTime collectedAt = LocalDateTime.now();
}
