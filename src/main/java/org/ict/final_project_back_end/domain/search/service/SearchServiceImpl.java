package org.ict.final_project_back_end.domain.search.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.dto.GameSummaryResponse;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.search.client.KeywordExtractionException;
import org.ict.final_project_back_end.domain.search.client.KeywordExtractor;
import org.ict.final_project_back_end.domain.search.client.SearchKeywords;
import org.ict.final_project_back_end.domain.search.client.SearchModelClient;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelRequest;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelResult;
import org.ict.final_project_back_end.domain.search.dto.NaturalSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// AI 호출과 모델 서버 호출이 느릴 수 있어 서비스 전체에는 트랜잭션을 걸지 않는다 (DB 조회는 마지막에 한 번뿐이다).
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

  private final KeywordExtractor keywordExtractor;
  private final SearchModelClient modelClient;
  private final GameRepository gameRepository;
  private final SearchRateLimit rateLimit;

  public SearchServiceImpl(KeywordExtractor keywordExtractor, SearchModelClient modelClient,
                           GameRepository gameRepository, SearchRateLimit rateLimit) {
    this.keywordExtractor = keywordExtractor;
    this.modelClient = modelClient;
    this.gameRepository = gameRepository;
    this.rateLimit = rateLimit;
  }

  @Override
  public NaturalSearchResponse search(Long userId, String query, int limit) {
    String text = query.trim();
    rateLimit.check(userId);

    // AI 분석이 실패해도 검색은 계속한다: 검색어를 그대로 설명 문장으로 쓴다 (모델의 다국어 임베딩이 한국어도 어느 정도 이해한다)
    SearchKeywords keywords;
    boolean analyzed = true;
    try {
      keywords = keywordExtractor.extract(text);
    } catch (KeywordExtractionException e) {
      log.warn("검색어 분석 실패, 검색어를 그대로 사용: {}", e.getMessage());
      keywords = SearchKeywords.fallback(text);
      analyzed = false;
    }
    if (!keywords.hasPositive()) { // 제외 태그만 있으면 찾을 대상이 없으므로 검색어를 설명 문장으로 쓴다
      keywords = keywords.withDescription(text);
    }

    SearchModelResult result = modelClient.search(SearchModelRequest.of(keywords, limit));
    return toResponse(text, keywords, analyzed, result);
  }

  private NaturalSearchResponse toResponse(String query, SearchKeywords keywords, boolean analyzed, SearchModelResult result) {
    List<SearchModelResult.Item> items = result.items();
    Map<Long, Game> gamesByAppId = gameRepository.findAllById(items.stream().map(SearchModelResult.Item::appId).toList())
        .stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));
    List<NaturalSearchResponse.Item> results = IntStream.range(0, items.size())
        .mapToObj(i -> {
          SearchModelResult.Item item = items.get(i);
          Game game = gamesByAppId.get(item.appId());
          return new NaturalSearchResponse.Item(i + 1, item.appId(), item.score(),
              game == null ? null : GameSummaryResponse.from(game));
        })
        .toList();
    List<String> ignoredTags = result.ignoredTags() == null ? List.of() : result.ignoredTags();
    return new NaturalSearchResponse(query,
        new NaturalSearchResponse.Analysis(analyzed, keywords.tags(), keywords.excludeTags(), keywords.description(),
            keywords.similarTo(), ignoredTags),
        results);
  }
}
