package org.ict.final_project_back_end.domain.purchase.entity;

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
import java.time.LocalDateTime;

// 스팀 "라이선스 및 제품 키 활성화" 페이지에서 가져온 라이선스 한 건 (금액 없음).
// 구매내역에 결제 기록이 없는 게임(선물·무료 배포·제품 키)의 획득일과 획득 방법을 알려준다.
@Entity
@Table(name = "game_licenses", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "external_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameLicense {

  // 획득 방법
  public static final String STORE = "STORE";
  public static final String GIFT = "GIFT";
  public static final String COMPLIMENTARY = "COMPLIMENTARY";
  public static final String KEY = "KEY";
  public static final String OTHER = "OTHER";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "external_key", nullable = false, length = 32)
  private String externalKey;

  // 라이선스 페이지에 적힌 이름 그대로 (게임 이름이 아니라 패키지 이름일 수 있음)
  @Column(name = "raw_name", nullable = false, length = 500)
  private String rawName;

  // 보유 게임에 연결되면 그 appId, 아니면 null
  @Column(name = "app_id")
  private Long appId;

  @Column(name = "acquired_at", nullable = false)
  private LocalDate acquiredAt;

  @Column(nullable = false, length = 20)
  private String method;

  // 사용자가 지울 수 있는 무료 라이선스(무료 게임·무료 배포)인지
  @Column(name = "free_license", nullable = false)
  private boolean freeLicense;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
