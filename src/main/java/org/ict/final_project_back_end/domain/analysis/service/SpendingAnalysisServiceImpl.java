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
import org.ict.final_project_back_end.domain.purchase.repository.PurchaseTransactionRepository;
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

  private static final String LIST_PRICE_BASIS = "KRW_LIST_PRICE";
  private static final String HISTORY_BASIS = "PURCHASE_HISTORY";
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
  private final PurchaseTransactionRepository purchaseRepository;

  public SpendingAnalysisServiceImpl(UserRepository userRepository, LibraryGameRepository libraryGameRepository,
                                     GameRepository gameRepository, GamePriceKrwRepository priceRepository,
                                     GamePriceKrwService priceService,
                                     PurchaseTransactionRepository purchaseRepository) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.gameRepository = gameRepository;
    this.priceRepository = priceRepository;
    this.priceService = priceService;
    this.purchaseRepository = purchaseRepository;
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

    // 구매내역을 가져왔으면 실제 결제액으로, 아니면 현재 한국 정가로 추정한다
    PurchaseAggregator.Result purchases = PurchaseAggregator.aggregate(purchaseRepository.findAllWithItemsByUserId(userId));
    boolean hasHistory = !purchases.transactions().isEmpty() || purchases.unconvertedCount() > 0;

    List<SpendingGameResponse> result = owned.stream()
        .map(libraryGame -> toResponse(libraryGame, games.get(libraryGame.getAppId()),
            prices.get(libraryGame.getAppId()), hasHistory, purchases.byGame().get(libraryGame.getAppId())))
        .toList();
    // 보유하지 않은 게임(환불·선물 등)에 쓴 돈도 실제 지출이므로 나눌 수 없는 지출에 더한다
    int notOwnedKrw = purchases.byGame().entrySet().stream()
        .filter(entry -> !appIds.contains(entry.getKey()))
        .mapToInt(entry -> entry.getValue().paidKrw() - entry.getValue().refundKrw())
        .sum();
    List<SpendingAnalysisResponse.PurchaseView> views = purchases.transactions().stream()
        .map(v -> new SpendingAnalysisResponse.PurchaseView(v.purchasedAt(), v.type(), v.amountKrw(),
            v.appId() != null && appIds.contains(v.appId()) ? v.appId() : null, v.items(), v.savedKrw()))
        .toList();
    return new SpendingAnalysisResponse(LocalDate.now(), hasHistory ? HISTORY_BASIS : LIST_PRICE_BASIS, result,
        hasHistory, purchases.unallocatedKrw() + notOwnedKrw, purchases.savedKrw(), purchases.unconvertedCount(),
        purchases.unmatchedItemCount(), views);
  }

  private SpendingGameResponse toResponse(LibraryGame owned, Game game, GamePriceKrw price, boolean hasHistory,
                                          PurchaseAggregator.GameSpend spend) {
    List<String> genres = splitGenres(game == null ? null : game.getGenre());
    boolean priced = price != null && GamePriceKrw.PRICED.equals(price.getStatus());
    boolean free = price != null && GamePriceKrw.FREE.equals(price.getStatus());
    Integer amount;
    String amountSource;
    if (spend != null) {
      amount = spend.paidKrw();
      amountSource = "CONFIRMED";
    } else if (free) {
      amount = 0;
      amountSource = "FREE";
    } else if (hasHistory) {
      // 구매내역은 있는데 이 게임은 결제 기록이 없다: 선물·키 등록·무료 배포 등으로 얻은 게임
      amount = null;
      amountSource = "NO_RECORD";
    } else if (priced) {
      amount = price.getInitialKrw();
      amountSource = "ESTIMATED";
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
        owned.getLastPlayedAt(),
        priced ? price.getInitialKrw() : null,
        spend == null ? 0 : spend.refundKrw(),
        spend == null ? null : spend.firstPurchasedAt(),
        spend == null ? 0 : spend.dlcCount(),
        spend == null ? 0 : spend.savedKrw());
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

  // 스팀 플레이 시간은 분 단위라 시간 단위로 바꾼다. 반올림은 화면에서 표시할 때만 한다
  // (여기서 0.1시간 단위로 반올림하면 3분 플레이도 6분으로 계산돼 시간당 비용이 크게 틀어진다)
  private double toHours(Integer minutes) {
    return minutes == null ? 0 : minutes / 60.0;
  }
}
