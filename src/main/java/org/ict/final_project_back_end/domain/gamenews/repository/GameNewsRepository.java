package org.ict.final_project_back_end.domain.gamenews.repository;

import org.ict.final_project_back_end.domain.gamenews.entity.GameNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface GameNewsRepository extends JpaRepository<GameNews, Long> {
  Optional<GameNews> findByGame_AppIdAndSourceNewsId(Long appId, String sourceNewsId);

  Page<GameNews> findByGame_AppIdOrderByPublishedAtDescIdDesc(Long appId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from GameNews n where n.game.appId = :appId and n.sourceNewsId = :sourceId")
  Optional<GameNews> findForUpdate(@Param("appId") long appId, @Param("sourceId") String sourceId);

  @Query("select n.id from GameNews n where n.game.appId = :appId order by n.publishedAt desc, n.id desc")
  List<Long> findLatestIds(@Param("appId") long appId, Pageable pageable);

  // Scan all stored news for this game, so failed or missing briefings outside the latest collection window recover.
  @Query("""
      select n.id from GameNews n left join GameNewsBriefing b on b.newsId = n.id
      where n.game.appId = :appId and (
        b.newsId is null or (
          (b.generatedAt is null or b.aiContent is null or b.summary is null or b.category is null
            or b.generatedFromHash is null or b.generatedFromHash <> n.contentHash)
          and (
            b.generationStatus in (org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus.PENDING,
              org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus.SUCCEEDED)
            or (b.generationStatus = org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus.FAILED
              and b.nextRetryAt <= :now)
            or (b.generationStatus = org.ict.final_project_back_end.domain.gamenews.entity.BriefingGenerationStatus.PROCESSING
              and (b.processingStartedAt is null or b.processingStartedAt <= :expiredBefore))
          )
        )
      ) order by n.id
      """)
  List<Long> findBriefingCandidates(@Param("appId") long appId, @Param("now") LocalDateTime now,
      @Param("expiredBefore") LocalDateTime expiredBefore, Pageable pageable);
}
