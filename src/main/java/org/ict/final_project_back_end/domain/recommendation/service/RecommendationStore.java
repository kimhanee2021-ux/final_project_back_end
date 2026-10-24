package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.recommendation.entity.RecommendedGame;
import org.ict.final_project_back_end.domain.recommendation.repository.RecommendedGameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 저장된 추천 목록의 재사용 판단과 쓰기를 모았다. 쓰기는 모델 서버 호출(느림)과 같은 트랜잭션에 묶이지 않도록 서비스와 분리했다.
@Component
public class RecommendationStore {

  private final RecommendedGameRepository repository;
  private final Duration cacheTtl;

  public RecommendationStore(RecommendedGameRepository repository,
                             @Value("${app.recommend.cache-ttl-hours}") long cacheTtlHours) {
    this.repository = repository;
    this.cacheTtl = Duration.ofHours(cacheTtlHours);
  }

  // 저장본이 limit개 이상이고 TTL 안이면 순위 순으로 돌려준다. 아니면 비어 있다.
  public Optional<List<RecommendedGame>> findReusable(Long userId, int limit) {
    List<RecommendedGame> saved = repository.findAllByUserIdOrderByRankAsc(userId);
    if (saved.size() < limit) {
      return Optional.empty();
    }
    boolean fresh = saved.get(0).getGeneratedAt().isAfter(LocalDateTime.now().minus(cacheTtl));
    return fresh ? Optional.of(saved) : Optional.empty();
  }

  // 사용자의 추천 목록을 한 세트째로 교체한다. 일부만 바뀐 상태로 읽히지 않게 한 트랜잭션에서 처리한다.
  @Transactional
  public void replace(Long userId, List<RecommendedGame> recommendations) {
    repository.deleteByUserId(userId);
    repository.flush(); // 같은 (user_id, rank_no)를 다시 넣기 전에 삭제가 먼저 반영돼야 유니크 제약에 걸리지 않는다
    repository.saveAll(recommendations);
  }

  // 보유 게임이 바뀌어 저장된 추천이 더는 맞지 않을 때 지운다. 다음 조회에서 새로 계산한다.
  @Transactional
  public void invalidate(Long userId) {
    repository.deleteByUserId(userId);
  }
}
