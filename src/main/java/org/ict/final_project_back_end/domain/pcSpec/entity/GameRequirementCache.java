package org.ict.final_project_back_end.domain.pcSpec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게임별 최소/권장 사양 파싱 캐시 - 최소사양 체크
@Entity
@Table(name = "game_requirements_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameRequirementCache {

  @Id
  @Column(name = "app_id")
  private Long appId;

  @Column(name = "min_cpu_model", length = 100)
  private String minCpuModel;

  @Column(name = "min_gpu_model", length = 100)
  private String minGpuModel;

  @Column(name = "rec_cpu_model", length = 100)
  private String recCpuModel;

  @Column(name = "rec_gpu_model", length = 100)
  private String recGpuModel;

  @Column(name = "parsed_at")
  private LocalDateTime parsedAt;
}
