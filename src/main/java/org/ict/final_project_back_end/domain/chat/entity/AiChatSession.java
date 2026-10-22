package org.ict.final_project_back_end.domain.chat.entity;

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

// AI 챗봇 대화 세션(코칭/미니게임 공용)
@Entity
@Table(name = "ai_chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AiChatSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(length = 100)
  private String title;

  @Builder.Default
  @Column(name = "session_type", nullable = false, length = 30)
  private String sessionType = "COACHING";

  @Builder.Default
  @Column(name = "is_private", nullable = false, columnDefinition = "TINYINT(1)")
  private Boolean privateSession = false;

  @Builder.Default
  @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1)")
  private Boolean active = true;

  // 이전 응답을 이어 받기 위한 AI API의 응답 id
  @Column(name = "last_response_id", length = 100)
  private String lastResponseId;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // ddl-auto로 만든 테이블에는 ON UPDATE CURRENT_TIMESTAMP가 없으므로 JPA가 수정 시각을 채운다
  @PreUpdate
  void touchUpdatedAt() {
    this.updatedAt = LocalDateTime.now();
  }
}
