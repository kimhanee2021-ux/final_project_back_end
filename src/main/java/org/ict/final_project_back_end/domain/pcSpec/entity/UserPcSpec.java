package org.ict.final_project_back_end.domain.pcSpec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.time.LocalDateTime;

// 유저 확정 PC 사양. 사용자당 1건이라 user_id를 PK로 쓴다.
@Entity
@Table(name = "user_pc_specs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserPcSpec {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "cpu_model", length = 100)
  private String cpuModel;

  @Column(name = "gpu_model", length = 100)
  private String gpuModel;

  @Column(name = "ram_tier", length = 20)
  private String ramTier;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
