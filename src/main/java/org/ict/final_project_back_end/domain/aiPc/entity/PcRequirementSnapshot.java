package org.ict.final_project_back_end.domain.aiPc.entity;

import jakarta.persistence.*;
import org.ict.final_project_back_end.domain.aiPc.dto.PcGameRequirements;

@Entity
@Table(
    name = "pc_requirement_snapshots",
    indexes = @Index(name = "idx_pc_req_app_checked", columnList = "appId,checkedAt"))
public class PcRequirementSnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private long appId;

  @Column(length = 200, nullable = false)
  private String name;

  @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
  private String minimumText;

  @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
  private String recommendedText;

  private boolean windowsSupported;

  @Column(length = 50, nullable = false)
  private String checkedAt;

  @Column(length = 300)
  private String sourceUrl;

  protected PcRequirementSnapshot() {}

  public PcRequirementSnapshot(PcGameRequirements d) {
    appId = d.appId();
    name = d.name();
    minimumText = d.minimum();
    recommendedText = d.recommended();
    windowsSupported = d.windows();
    checkedAt = d.checkedAt();
    sourceUrl = d.source();
  }

  public PcGameRequirements data() {
    return new PcGameRequirements(
        appId,
        name,
        minimumText,
        recommendedText,
        windowsSupported,
        sourceUrl == null ? "https://store.steampowered.com/app/" + appId + "/" : sourceUrl,
        checkedAt);
  }
}
