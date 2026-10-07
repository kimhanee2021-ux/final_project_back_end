package org.ict.final_project_back_end.domain.beta.entity;

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
import org.ict.final_project_back_end.domain.board.entity.BoardPost;

import java.time.LocalDateTime;

// 베타테스트 상세(board_posts 1:1 확장). 게시글 id를 그대로 PK로 쓴다.
@Entity
@Table(name = "beta_test_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BetaTestDetail {

  @Id
  @Column(name = "post_id")
  private Long postId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id")
  private BoardPost post;

  @Column(length = 100)
  private String genre;

  @Column(length = 30)
  private String stage;

  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Column(name = "build_link_url", length = 500)
  private String buildLinkUrl;

  // 테스터에게 보여줄 질문 목록(JSON 문자열 그대로 저장)
  @Column(name = "questions_json", columnDefinition = "JSON")
  private String questionsJson;

  private LocalDateTime deadline;
}
