package org.ict.final_project_back_end.domain.notice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.ict.final_project_back_end.domain.user.entity.User;

@Entity
@Table(name = "notices", indexes = @Index(name = "idx_notices_created_id", columnList = "created_at,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, length = 255)
  private String title;
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id")
  @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
  private User author;
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public Notice(String title, String content, User author) {
    this.title = title;
    this.content = content;
    this.author = author;
    this.createdAt = LocalDateTime.now();
  }

  public void update(String title, String content) {
    this.title = title;
    this.content = content;
    this.updatedAt = LocalDateTime.now();
  }
}
