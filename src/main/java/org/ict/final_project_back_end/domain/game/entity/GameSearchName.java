package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Existing search-name table. BBS only reads aliases; it never rebuilds the catalog. */
@Entity
@Immutable
@Table(name = "game_search_names")
public class GameSearchName {
  public enum Kind { CANONICAL, ALIAS }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false)
  private Game game;
  @Column(name = "display_name", nullable = false, length = 255)
  private String displayName;
  @Column(name = "normalized_name", nullable = false, length = 255)
  private String normalizedName;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
  private Kind kind;
  @Column(length = 32)
  private String locale;
  @Column(nullable = false, length = 100)
  private String source;
  @Column(nullable = false)
  private boolean active;

  protected GameSearchName() {}

  public GameSearchName(Game game, String displayName, String normalizedName, Kind kind, String source) {
    this.game = game;
    this.displayName = displayName;
    this.normalizedName = normalizedName;
    this.kind = kind;
    this.source = source;
    this.active = true;
  }
}
