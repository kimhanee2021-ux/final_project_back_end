package org.ict.final_project_back_end.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;

import java.time.LocalDateTime;

// 구매 전 감정 체크 - 가치판단 도우미. 보유 게임 1건당 1개.
@Entity
@Table(name = "purchase_emotions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PurchaseEmotion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 설계안의 user_owned_games는 코드상 LibraryGame(library_games)이라 이쪽을 참조한다
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_owned_game_id", nullable = false, unique = true)
  private LibraryGame libraryGame;

  @Column(nullable = false, length = 20)
  private String emotion;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
