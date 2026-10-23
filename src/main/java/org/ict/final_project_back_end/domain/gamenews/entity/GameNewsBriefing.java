package org.ict.final_project_back_end.domain.gamenews.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 원문 한 건의 최신 성공 브리핑과 생성 상태. 재생성 중/실패 시 이전 성공본을 보존한다. */
@Entity
@Table(name = "game_news_briefings", indexes = {
    @Index(name = "idx_game_news_briefing_retry", columnList = "generation_status,next_retry_at,news_id"),
    @Index(name = "idx_game_news_briefing_category", columnList = "category,news_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameNewsBriefing {
  @Id
  @Column(name = "news_id")
  private Long newsId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "news_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_game_news_briefing_news"))
  private GameNews news;

  @Enumerated(EnumType.STRING)
  @Column(length = 30, columnDefinition = "VARCHAR(30)")
  private GameNewsCategory category;

  @Column(length = 500)
  private String summary;

  @Column(name = "ai_content", columnDefinition = "TEXT")
  private String aiContent;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "generation_status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
  private BriefingGenerationStatus generationStatus = BriefingGenerationStatus.PENDING;

  @Column(name = "generated_from_hash", length = 64)
  private String generatedFromHash;

  @Column(name = "model_name", length = 100)
  private String modelName;

  @Column(name = "prompt_version", length = 30)
  private String promptVersion;

  @Column(name = "generated_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime generatedAt;

  @Column(name = "processing_started_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime processingStartedAt;

  @Builder.Default
  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "next_retry_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime nextRetryAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  public void markPending() {
    generationStatus = BriefingGenerationStatus.PENDING;
    nextRetryAt = null;
    retryCount = 0;
    lastError = null;
  }

  public void startProcessing(LocalDateTime startedAt) {
    generationStatus = BriefingGenerationStatus.PROCESSING;
    processingStartedAt = startedAt;
    nextRetryAt = null;
    lastError = null;
  }

  public boolean ownsAttempt(LocalDateTime startedAt) {
    return generationStatus == BriefingGenerationStatus.PROCESSING
        && java.util.Objects.equals(processingStartedAt, startedAt);
  }

  public void complete(GameNewsCategory category, String summary, String content, String hash,
                       String model, String prompt, LocalDateTime now) {
    this.category = category; this.summary = summary; this.aiContent = content;
    generatedFromHash = hash; modelName = model; promptVersion = prompt; generatedAt = now;
    generationStatus = BriefingGenerationStatus.SUCCEEDED;
    retryCount = 0; nextRetryAt = null; lastError = null;
    // Retain the latest attempt timestamp so subsequent claims can always choose a newer token.
  }

  public void fail(LocalDateTime now, String errorCode) {
    generationStatus = BriefingGenerationStatus.FAILED;
    if (retryCount < Integer.MAX_VALUE) retryCount++;
    nextRetryAt = now.plusMinutes(Math.min(360L, 5L << Math.min(retryCount - 1, 7)));
    lastError = errorCode.substring(0, Math.min(errorCode.length(), 1000));
    // Retain the attempt time for diagnosis; only PROCESSING represents an active lease.
  }
}
