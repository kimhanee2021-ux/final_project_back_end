package org.ict.final_project_back_end.domain.analysis.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.purchase.entity.GameLicense;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseItem;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseTransaction;
import org.ict.final_project_back_end.domain.purchase.repository.GameLicenseRepository;
import org.ict.final_project_back_end.domain.purchase.repository.PurchaseTransactionRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.exception.SteamNotLinkedException;
import org.ict.final_project_back_end.domain.user.exception.UserNotFoundException;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 클래스 전체에 트랜잭션을 걸지 않고, 각 조회/저장은 repository 단위로 짧게 끝낸다.
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
  private final PurchaseTransactionRepository purchaseRepository;
  private final GameLicenseRepository licenseRepository;

  public SpendingAnalysisServiceImpl(UserRepository userRepository, LibraryGameRepository libraryGameRepository,
                                     GameRepository gameRepository,
                                     PurchaseTransactionRepository purchaseRepository,
                                     GameLicenseRepository licenseRepository) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.gameRepository = gameRepository;
    this.purchaseRepository = purchaseRepository;
    this.licenseRepository = licenseRepository;
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
    Map<Long, Game> games = gameRepository.findAllById(appIds).stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));

    // 구매내역을 가져왔으면 실제 결제액으로, 아니면 현재 한국 정가로 추정한다
    List<PurchaseTransaction> history = purchaseRepository.findAllWithItemsByUserId(userId);
    PurchaseAggregator.Result purchases = PurchaseAggregator.aggregate(history, listPrices(history, games));
    boolean hasHistory = !purchases.transactions().isEmpty() || purchases.unconvertedCount() > 0;

    // 라이선스 페이지를 가져왔으면 게임별로 가장 먼저 계정에 추가된 라이선스(획득일·방법)
    Map<Long, GameLicense> firstLicense = new HashMap<>();
    licenseRepository.findMatchedByUserId(userId).forEach(license -> firstLicense.putIfAbsent(license.getAppId(), license));

    List<SpendingGameResponse> result = owned.stream()
        .map(libraryGame -> toResponse(libraryGame, games.get(libraryGame.getAppId()),
            hasHistory, purchases.byGame().get(libraryGame.getAppId()),
            firstLicense.get(libraryGame.getAppId())))
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

  private SpendingGameResponse toResponse(LibraryGame owned, Game game, boolean hasHistory,
                                          PurchaseAggregator.GameSpend spend, GameLicense license) {
    List<String> genres = splitGenres(game == null ? null : game.getGenre());
    // games.original_price(원화 정가): 0이면 무료, null이면 가격을 알 수 없음
    Integer listPrice = game == null || game.getOriginalPrice() == null ? null : game.getOriginalPrice().intValue();
    boolean priced = listPrice != null && listPrice > 0;
    boolean free = listPrice != null && listPrice == 0;
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
      amount = listPrice;
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
        null,
        null,
        toHours(owned.getPlaytimeForever()),
        toHours(owned.getPlaytime2Weeks()),
        owned.getLastPlayedAt(),
        priced ? listPrice : null,
        spend == null ? 0 : spend.refundKrw(),
        spend == null ? null : spend.firstPurchasedAt(),
        spend == null ? 0 : spend.dlcCount(),
        spend == null ? 0 : spend.savedKrw(),
        spend != null && spend.bundleCount() > 0,
        license == null ? null : license.getAcquiredAt(),
        license == null ? null : license.getMethod());
  }

  // 여러 게임이 섞인 번들을 나눌 비율로 쓰는 한국 정가 (보유하지 않은 게임도 포함해 games에서 한 번에 조회)
  private Map<Long, Integer> listPrices(List<PurchaseTransaction> history, Map<Long, Game> ownedGames) {
    Map<Long, Integer> result = new HashMap<>();
    List<Long> others = new ArrayList<>();
    history.stream().flatMap(tx -> tx.getItems().stream()).map(PurchaseItem::targetAppId).filter(Objects::nonNull)
        .distinct().forEach(appId -> {
          Game game = ownedGames.get(appId);
          if (game == null) {
            others.add(appId);
          } else {
            putListPrice(result, game);
          }
        });
    gameRepository.findAllById(others).forEach(game -> putListPrice(result, game));
    return result;
  }

  private void putListPrice(Map<Long, Integer> result, Game game) {
    if (game.getOriginalPrice() != null && game.getOriginalPrice().signum() > 0) {
      result.put(game.getAppId(), game.getOriginalPrice().intValue());
    }
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
