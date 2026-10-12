package org.ict.final_project_back_end.domain.analysis.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GamePriceKrw;
import org.ict.final_project_back_end.domain.game.repository.GamePriceKrwRepository;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.service.GamePriceKrwService;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.ict.final_project_back_end.global.exception.SteamNotLinkedException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 스팀 호출(가격 보충)이 섞여 있어서 클래스 전체에 트랜잭션을 걸지 않는다.
// 각 조회/저장은 repository 단위로 짧게 끝나고, 외부 호출 동안 DB 연결을 붙잡지 않는다.
@Service
public class SpendingAnalysisServiceImpl implements SpendingAnalysisService {

  private static final String PRICE_BASIS = "KRW_LIST_PRICE";
  // 스팀은 장르를 알파벳순으로 저장해서 앞에서부터 고르면 대부분 "Action"이 된다(ELDEN RING = "Action,RPG").
  // 그래서 카탈로그에서 드문(=더 구체적인) 장르를 우선한다. 목록에 없는 장르는 이 뒤에 온다.
  private static final List<String> GENRE_PRIORITY = List.of(
      "Massively Multiplayer", "Racing", "Sports", "RPG", "Strategy", "Simulation", "Adventure", "Action", "Casual");
  // 유통/가격 형태를 나타내는 값은 다른 장르가 하나도 없을 때만 대표 장르로 쓴다
  private static final Set<String> NON_REPRESENTATIVE_GENRES = Set.of("Indie", "Free To Play", "Early Access");

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final GameRepository gameRepository;
  private final GamePriceKrwRepository priceRepository;
  private final GamePriceKrwService priceService;

  public SpendingAnalysisServiceImpl(UserRepository userRepository, LibraryGameRepository libraryGameRepository,
                                     GameRepository gameRepository, GamePriceKrwRepository priceRepository,
                                     GamePriceKrwService priceService) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.gameRepository = gameRepository;
    this.priceRepository = priceRepository;
    this.priceService = priceService;
  }

  @Override
  public SpendingAnalysisResponse getMySpending(Long userId) {
    if (userId == null) {
      throw new AuthenticationRequiredException();
    }
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.getSteamId() == null) {
      throw new SteamNotLinkedException();
    }

    List<LibraryGame> owned = libraryGameRepository.findAllByUserIdOrderByNameAsc(userId);
    List<Long> appIds = owned.stream().map(LibraryGame::getAppId).toList();
    // 가격이 없거나 하루 지난 보유 게임만 스팀에서 보충한다 (CSV에 없는 게임 포함)
    priceService.ensurePrices(appIds);
    Map<Long, Game> games = gameRepository.findAllById(appIds).stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));
    Map<Long, GamePriceKrw> prices = priceRepository.findAllById(appIds).stream()
        .collect(Collectors.toMap(GamePriceKrw::getAppId, Function.identity()));

    List<SpendingGameResponse> result = owned.stream()
        .map(libraryGame -> toResponse(libraryGame, games.get(libraryGame.getAppId()),
            prices.get(libraryGame.getAppId())))
        .toList();
    return new SpendingAnalysisResponse(LocalDate.now(), PRICE_BASIS, result);
  }

  private SpendingGameResponse toResponse(LibraryGame owned, Game game, GamePriceKrw price) {
    List<String> genres = splitGenres(game == null ? null : game.getGenre());
    Integer amount;
    String amountSource;
    if (price != null && GamePriceKrw.PRICED.equals(price.getStatus())) {
      amount = price.getInitialKrw();
      amountSource = "ESTIMATED";
    } else if (price != null && GamePriceKrw.FREE.equals(price.getStatus())) {
      amount = 0;
      amountSource = "FREE";
    } else {
      amount = null;
      amountSource = "UNKNOWN";
    }
    return new SpendingGameResponse(
        owned.getAppId(),
        owned.getName(),
        representativeGenre(genres),
        genres,
        game != null && game.getThumbnailUrl() != null ? game.getThumbnailUrl() : owned.getIconUrl(),
        amount,
        amountSource,
        price == null ? null : price.getFinalKrw(),
        price == null ? null : price.getDiscountPercent(),
        toHours(owned.getPlaytimeForever()),
        toHours(owned.getPlaytime2Weeks()),
        owned.getLastPlayedAt());
  }

  private List<String> splitGenres(String genre) {
    if (genre == null || genre.isBlank()) {
      return List.of();
    }
    return Arrays.stream(genre.split(",")).map(String::trim).filter(g -> !g.isEmpty()).toList();
  }

  private String representativeGenre(List<String> genres) {
    return genres.stream()
        .filter(g -> !NON_REPRESENTATIVE_GENRES.contains(g))
        .min(Comparator.comparingInt(this::genreRank))
        .orElse(genres.isEmpty() ? null : genres.get(0));
  }

  private int genreRank(String genre) {
    int rank = GENRE_PRIORITY.indexOf(genre);
    return rank < 0 ? GENRE_PRIORITY.size() : rank;
  }

  // 스팀 플레이 시간은 분 단위라 시간 단위(소수 첫째 자리)로 바꾼다
  private double toHours(Integer minutes) {
    return minutes == null ? 0 : Math.round(minutes / 6.0) / 10.0;
  }
}
