package org.ict.final_project_back_end.domain.beta.entity;

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

import java.time.LocalDateTime;

// 베타 피드백 - 피드백 서비스. 참여 1건당 피드백 1건.
@Entity
@Table(name = "beta_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BetaFeedback {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "participation_id", nullable = false, unique = true)
  private TestParticipation participation;

  @Column(columnDefinition = "TINYINT")
  private Integer rating;

  @Column(name = "answers_json", columnDefinition = "JSON")
  private String answersJson;

  @Column(name = "bug_report", length = 2000)
  private String bugReport;

  @Builder.Default
  @Column(nullable = false, length = 20)
  private String status = "UNDECIDED";

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
