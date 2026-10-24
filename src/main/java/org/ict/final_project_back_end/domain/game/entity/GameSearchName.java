package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "game_search_names", indexes = {@Index(name = "idx_game_search_normalized", columnList = "normalized_name"),
    @Index(name = "idx_game_search_compact", columnList = "compact_name")},
    uniqueConstraints = @UniqueConstraint(name = "uk_game_search_name", columnNames = {"app_id", "normalized_name"}))
public class GameSearchName {
  public enum Kind { CANONICAL, ALIAS }
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false) @OnDelete(action = OnDeleteAction.CASCADE)
  private Game game;
  @Column(name = "display_name", nullable = false, length = 255) private String displayName;
  @Column(name = "normalized_name", nullable = false, length = 255) private String normalizedName;
  @Column(name = "compact_name", length = 255) private String compactName;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Kind kind;
  @Column(length = 32) private String locale;
  @Column(nullable = false, length = 100) private String source;
  @Column(nullable = false) private boolean active;
  protected GameSearchName() {}
  public GameSearchName(Game game, String displayName, String normalizedName, Kind kind, String source) {
    this.game = game; this.displayName = displayName; this.normalizedName = normalizedName;
    this.kind = kind; this.source = source; this.active = !normalizedName.isBlank(); refreshSearchKey();
  }
  private static final java.util.concurrent.atomic.AtomicLong REVISION = new java.util.concurrent.atomic.AtomicLong();
  public static void searchKeysChanged() { REVISION.incrementAndGet(); }
  public static long revision() { return REVISION.get(); }
  @PrePersist @PreUpdate
  public void refreshSearchKey() {
    compactName = org.ict.final_project_back_end.domain.game.service.GameVoiceLexicon.key(normalizedName);
  }
  @PostPersist @PostUpdate @PostRemove
  void invalidateSearches() {
    if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
      org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
          new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { REVISION.incrementAndGet(); }
          });
    else REVISION.incrementAndGet();
  }
  public Long appId() { return game.getAppId(); }
  public String normalizedName() { return normalizedName; }
  public Kind kind() { return kind; }
  public String displayName() { return displayName; }
  public String source() { return source; }
  public boolean active() { return active; }
  public void canonical(String displayName) {
    this.displayName = displayName; this.kind = Kind.CANONICAL; this.source = "games"; this.active = !normalizedName.isBlank(); refreshSearchKey();
  }
}
