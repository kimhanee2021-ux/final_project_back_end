package org.ict.final_project_back_end.domain.chat.entity;

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

// 메시지 점수 태깅. 메시지 하나에 점수 종류별로 1건.
@Entity
@Table(name = "ai_chat_message_scores", uniqueConstraints = @UniqueConstraint(name = "uq_score", columnNames = {"message_id", "score_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AiChatMessageScore {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "message_id", nullable = false)
  private AiChatMessage message;

  @Column(name = "score_type", nullable = false, length = 50)
  private String scoreType;

  @Column(name = "score_value", nullable = false)
  private Integer scoreValue;
}
