package org.ict.final_project_back_end.domain.aisearch.service;

import org.ict.final_project_back_end.domain.aisearch.client.GameSearchModelClient;
import org.ict.final_project_back_end.domain.aisearch.client.SearchKeywordClient;
import org.ict.final_project_back_end.domain.aisearch.client.SearchKeywords;
import org.ict.final_project_back_end.domain.aisearch.dto.AiGameSearchResponse;
import org.ict.final_project_back_end.domain.aisearch.exception.AiSearchException;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 외부 호출(GPT, 검색 모델) 중에는 DB 커넥션을 잡고 있지 않도록 트랜잭션을 걸지 않는다
@Service
public class AiGameSearchServiceImpl implements AiGameSearchService {

  private static final Pattern MARKS = Pattern.compile("[™®©]");
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final Pattern HEAD_END = Pattern.compile("[:\\-–—(]");

  private final SearchKeywordClient keywordClient;
  private final GameSearchModelClient modelClient;
  private final GameRepository gameRepository;

  public AiGameSearchServiceImpl(SearchKeywordClient keywordClient, GameSearchModelClient modelClient,
                                 GameRepository gameRepository) {
    this.keywordClient = keywordClient;
    this.modelClient = modelClient;
    this.gameRepository = gameRepository;
  }

  @Override
  public AiGameSearchResponse search(String query, int limit) {
    String text = query == null ? "" : query.strip();
    if (text.length() < 2 || text.length() > MAX_QUERY_LENGTH) {
      throw new InvalidRequestException("검색 문장은 2~" + MAX_QUERY_LENGTH + "자로 적어 주세요");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidRequestException("limit은 1~" + MAX_LIMIT + " 사이여야 합니다");
    }

    SearchKeywords keywords = keywordClient.extract(text);
    if (keywords.intent().isControl()) {
      // 브리핑 조작(다음 소식·멈춤·계속)은 화면이 처리한다. 검색하지 않는다
      return new AiGameSearchResponse(text, keywords.intent().name(), null, keywordsOf(keywords), List.of(), null, List.of());
    }

    Game matched = keywords.gameTitle().isBlank() ? null : findGame(keywords.gameTitle()).orElse(null);
    if (keywords.hasNoKeywords() && matched == null) {
      throw AiSearchException.notUnderstood();
    }
    GameSearchModelClient.Response result = similarGames(keywords, matched, limit);

    List<Long> ids = new ArrayList<>(result.items().stream().map(GameSearchModelClient.Item::appId).toList());
    if (result.similarToAppId() != null) {
      ids.add(result.similarToAppId());
    }
    Map<Long, Game> games = ids.isEmpty() ? Map.of() : gameRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));

    // 말한 게임은 맨 위(matchedGame)에 따로 보여주므로 비슷한 게임 목록에서는 뺀다
    List<GameSearchModelClient.Item> similar = result.items().stream()
        .filter(item -> matched == null || item.appId() != matched.getAppId())
        .limit(limit)
        .toList();
    List<AiGameSearchResponse.Item> items = new ArrayList<>();
    for (GameSearchModelClient.Item item : similar) {
      items.add(new AiGameSearchResponse.Item(items.size() + 1, item.appId(), item.score(), info(games.get(item.appId()))));
    }
    Game similarGame = result.similarToAppId() == null ? null : games.get(result.similarToAppId());
    return new AiGameSearchResponse(
        text,
        keywords.intent().name(),
        matched == null ? null : new AiGameSearchResponse.MatchedGame(matched.getAppId(), info(matched)),
        keywordsOf(keywords),
        result.ignoredTags() == null ? List.of() : result.ignoredTags(),
        result.similarToAppId() == null ? null
            : new AiGameSearchResponse.SimilarTo(result.similarToAppId(), similarGame == null ? keywords.similarTo() : similarGame.getName()),
        items);
  }

  // 말한 게임과 비슷한 게임. 말한 게임을 찾았으면 키워드가 없거나 검색 모델이 못 알아들어도 그 게임만으로 결과를 낸다
  private GameSearchModelClient.Response similarGames(SearchKeywords keywords, Game matched, int limit) {
    SearchKeywords forModel = keywords;
    if (matched != null && keywords.similarTo().isBlank()) {
      forModel = new SearchKeywords(keywords.intent(), keywords.gameTitle(), keywords.tags(), keywords.excludeTags(),
          keywords.description(), matched.getName());
    }
    if (forModel.hasNoKeywords()) {
      return new GameSearchModelClient.Response(List.of(), List.of(), null);
    }
    try {
      // 말한 게임이 결과에 섞여 빠질 수 있어 하나 더 받는다
      return modelClient.search(forModel, Math.min(limit + 1, MAX_LIMIT + 1));
    } catch (AiSearchException e) {
      if (matched != null && "AI_SEARCH_NOT_UNDERSTOOD".equals(e.getCode())) {
        return new GameSearchModelClient.Response(List.of(), List.of(), null);
      }
      throw e;
    }
  }

  // 영어 공식 제목으로 게임을 찾는다: 정확히 같은 이름 -> 그 이름으로 시작하는 게임 (™®©·대소문자·공백 차이는 무시).
  // 후보는 제목 앞부분(첫 ":" 전까지)으로 시작하는 게임이다. "Call of Duty: ..."처럼 DB 이름에는 ®가 끼어 있어도 앞부분은 같다.
  Optional<Game> findGame(String title) {
    String name = normalize(title);
    if (name.isEmpty()) {
      return Optional.empty();
    }
    List<Game> candidates = gameRepository.findNameCandidates(escapeLike(head(name)) + "%");
    return candidates.stream().filter(game -> normalize(game.getName()).equals(name)).findFirst()
        .or(() -> candidates.stream().filter(game -> normalize(game.getName()).startsWith(name)).findFirst());
  }

  static String normalize(String title) {
    return SPACES.matcher(MARKS.matcher(title).replaceAll("")).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
  }

  // 후보를 찾을 앞부분: 첫 구분 기호(: - ( 등) 전까지. 너무 짧으면 이름 전체를 쓴다
  static String head(String normalizedName) {
    String head = HEAD_END.split(normalizedName, 2)[0].strip();
    return head.length() >= 2 ? head : normalizedName;
  }

  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private static AiGameSearchResponse.Keywords keywordsOf(SearchKeywords keywords) {
    return new AiGameSearchResponse.Keywords(keywords.gameTitle(), keywords.tags(), keywords.excludeTags(),
        keywords.description(), keywords.similarTo());
  }

  private static AiGameSearchResponse.GameInfo info(Game game) {
    return game == null ? null : new AiGameSearchResponse.GameInfo(
        game.getName(), game.getGenre(), game.getTags(), game.getThumbnailUrl(), game.getPositiveRate());
  }
}
