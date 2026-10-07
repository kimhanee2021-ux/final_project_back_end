package org.ict.final_project_back_end.domain.board.entity;

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

// 좋아요/싫어요/북마크(폴리모픽). target_type + target_id로 대상(게시글/댓글/리뷰 등)을 가리키므로 대상 쪽 FK는 없다.
@Entity
@Table(name = "content_interactions", uniqueConstraints = @UniqueConstraint(
    name = "uq_interaction", columnNames = {"target_type", "target_id", "user_id", "interaction_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ContentInteraction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "target_type", nullable = false, length = 30)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // LIKE / DISLIKE / BOOKMARK
  @Column(name = "interaction_type", nullable = false, length = 20)
  private String interactionType;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
