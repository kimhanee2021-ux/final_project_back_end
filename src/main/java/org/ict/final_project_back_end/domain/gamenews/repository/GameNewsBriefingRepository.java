package org.ict.final_project_back_end.domain.gamenews.repository;

import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsBriefing;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GameNewsBriefingRepository extends JpaRepository<GameNewsBriefing, Long> {
  // Scalar projections avoid fetching LONGTEXT source content or traversing lazy entities in the API.
  interface SummaryRow {
    Long getNewsId();
    Long getAppId();
    String getGameName();
    String getTitle();
    GameNewsCategory getCategory();
    String getSummary();
    String getSourceUrl();
    LocalDateTime getPublishedAt();
    LocalDateTime getSourceUpdatedAt();
    LocalDateTime getGeneratedAt();
    BriefingGenerationStatus getGenerationStatus();
    String getSourceHash();
    String getGeneratedFromHash();
  }

  interface DetailRow extends SummaryRow { String getAiContent(); }

  @Query(value = """
      select b.newsId as newsId, g.appId as appId, g.name as gameName, n.title as title,
             b.category as category, b.summary as summary, n.sourceUrl as sourceUrl,
             n.publishedAt as publishedAt, n.updatedAt as sourceUpdatedAt, b.generatedAt as generatedAt,
             b.generationStatus as generationStatus, n.contentHash as sourceHash,
             b.generatedFromHash as generatedFromHash
      from GameNewsBriefing b join b.news n join n.game g
      where b.generatedAt is not null and b.aiContent is not null
        and b.summary is not null and b.category is not null
        and (:appId is null or g.appId = :appId)
        and (:category is null or b.category = :category)
      order by n.publishedAt desc, n.id desc
      """, countQuery = """
      select count(b) from GameNewsBriefing b join b.news n
      where b.generatedAt is not null and b.aiContent is not null
        and b.summary is not null and b.category is not null
        and (:appId is null or n.game.appId = :appId)
        and (:category is null or b.category = :category)
      """)
  Page<SummaryRow> findPublishedSummaries(@Param("appId") Long appId,
      @Param("category") GameNewsCategory category, Pageable pageable);

  @Query("""
      select b.newsId as newsId, g.appId as appId, g.name as gameName, n.title as title,
             b.category as category, b.summary as summary, b.aiContent as aiContent,
             n.sourceUrl as sourceUrl, n.publishedAt as publishedAt, n.updatedAt as sourceUpdatedAt,
             b.generatedAt as generatedAt, b.generationStatus as generationStatus,
             n.contentHash as sourceHash, b.generatedFromHash as generatedFromHash
      from GameNewsBriefing b join b.news n join n.game g
      where g.appId = :appId and b.newsId = :newsId
        and b.generatedAt is not null and b.aiContent is not null
        and b.summary is not null and b.category is not null
      """)
  Optional<DetailRow> findPublishedDetail(@Param("appId") long appId, @Param("newsId") long newsId);

  // 재생성이 대기/실패 상태여도 이전 성공 결과가 있으면 조회할 수 있다.
  @Query("""
      select b from GameNewsBriefing b join b.news n
      where b.generatedAt is not null and b.aiContent is not null
        and (:appId is null or n.game.appId = :appId)
        and (:category is null or b.category = :category)
      order by n.publishedAt desc, n.id desc
      """)
  Page<GameNewsBriefing> findPublished(@Param("appId") Long appId,
      @Param("category") GameNewsCategory category, Pageable pageable);

  // 조회만 수행한다. 여러 작업자의 중복 실행을 막는 선점/잠금은 후속 처리 계층의 책임이다.
  @Query("""
      select b from GameNewsBriefing b
      where b.generationStatus = org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus.PENDING
         or (b.generationStatus = org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus.FAILED
             and b.nextRetryAt <= :now)
      order by b.newsId
      """)
  List<GameNewsBriefing> findReadyForGeneration(@Param("now") LocalDateTime now, Pageable pageable);
}
