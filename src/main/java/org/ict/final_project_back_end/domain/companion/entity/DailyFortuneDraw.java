package org.ict.final_project_back_end.domain.companion.entity;

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

import java.time.LocalDate;

// 운세/타로 뽑기. (user_id, draw_date) 유니크로 1일 1회를 DB에서 보장한다.
@Entity
@Table(name = "daily_fortune_draws", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "draw_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DailyFortuneDraw {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "draw_date", nullable = false)
  private LocalDate drawDate;

  @Column(name = "card_result_json", nullable = false, columnDefinition = "JSON")
  private String cardResultJson;

  @Column(name = "ai_message", length = 500)
  private String aiMessage;
}
