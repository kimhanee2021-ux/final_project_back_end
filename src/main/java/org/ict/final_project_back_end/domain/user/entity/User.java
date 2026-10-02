package org.ict.final_project_back_end.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Setter는 실제로 값이 바뀌어야 하는 nickname/passwordHash에만 붙인다.
// id/email/createdAt은 생성 시점(Builder) 이후로는 바뀌면 안 되는 값이라 setter를 열지 않는다.
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 스펙상 기본 생성자 필수 (무분별한 객체 생성 방지)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Setter
  @Column(nullable = false, unique = true)
  private String nickname;

  @Setter
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  // ddl-auto로 만든 테이블은 DB 기본값이 없으므로 엔티티 쪽에서 기본값을 채운다
  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
