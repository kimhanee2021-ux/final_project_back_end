package org.ict.final_project_back_end.domain.purchase.service;

import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportResponse;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseItem;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseTransaction;
import org.ict.final_project_back_end.domain.purchase.repository.PurchaseTransactionRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

// 스팀 구매내역 가져오기. 환율 조회와 게임 매칭(스팀 호출 포함)은 트랜잭션 밖에서 하고, 저장만 한 번에 한다.
@Service
public class PurchaseImportService {

  // 가져오기 한 번에 스팀에 물어볼 수 있는 최대 횟수 (DLC 확인, 이름 검색)
  private static final int MAX_STEAM_LOOKUPS = 60;

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final PurchaseTransactionRepository transactionRepository;
  private final ExchangeRateService exchangeRateService;
  private final GameNameMatcher gameNameMatcher;

  public PurchaseImportService(UserRepository userRepository, LibraryGameRepository libraryGameRepository,
                               PurchaseTransactionRepository transactionRepository,
                               ExchangeRateService exchangeRateService, GameNameMatcher gameNameMatcher) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.transactionRepository = transactionRepository;
    this.exchangeRateService = exchangeRateService;
    this.gameNameMatcher = gameNameMatcher;
  }

  public PurchaseImportResponse importHistory(Long userId, PurchaseImportRequest request) {
    User user = loadUser(userId);
    Set<String> existingKeys = transactionRepository.findExternalKeysByUserId(userId);
    Map<String, Long> library = new HashMap<>();
    for (LibraryGame game : libraryGameRepository.findAllByUserId(userId)) {
      library.putIfAbsent(game.getName(), game.getAppId());
    }

    GameNameMatcher.Budget budget = new GameNameMatcher.Budget(MAX_STEAM_LOOKUPS);
    Map<String, GameNameMatcher.Match> matchCache = new HashMap<>();
    Set<String> seenKeys = new HashSet<>();
    Set<String> unmatched = new LinkedHashSet<>();
    Set<String> unsupported = new TreeSet<>();
    List<PurchaseTransaction> toSave = new ArrayList<>();
    int duplicates = 0;

    for (PurchaseImportRequest.Transaction tx : request.transactions()) {
      if (existingKeys.contains(tx.externalKey()) || !seenKeys.add(tx.externalKey())) {
        duplicates++;
        continue;
      }
      Optional<ExchangeRateService.Rate> rate = tx.amount() == null
          ? Optional.empty()
          : exchangeRateService.rateToKrw(tx.currency(), tx.purchasedAt());
      if (tx.amount() != null && rate.isEmpty()) {
        unsupported.add(tx.currency() == null ? "알 수 없음" : tx.currency());
      }
      PurchaseTransaction transaction = PurchaseTransaction.builder()
          .user(user)
          .externalKey(tx.externalKey())
          .groupKey(tx.groupKey())
          .purchasedAt(tx.purchasedAt())
          .type(tx.type())
          .amount(tx.amount())
          .currency(tx.currency())
          .amountKrw(rate.map(r -> toKrw(tx.amount(), r.rateToKrw())).orElse(null))
          .exchangeRate(rate.map(ExchangeRateService.Rate::rateToKrw).orElse(null))
          .rateDate(rate.map(ExchangeRateService.Rate::rateDate).orElse(null))
          .originalAmount(tx.originalAmount())
          .discountPercent(tx.discountPercent())
          .build();

      // 구매내역에 appId가 적힌 건(게임 내 구매)은 상품이 하나일 때만 그 번호를 쓴다
      Long hint = tx.items().size() == 1 ? tx.appId() : null;
      for (String name : tx.items()) {
        GameNameMatcher.Match match = matchCache.computeIfAbsent(name + "|" + hint,
            key -> gameNameMatcher.match(name, hint, library, budget));
        if (PurchaseItem.UNMATCHED.equals(match.status())) {
          unmatched.add(name);
        }
        transaction.addItem(PurchaseItem.builder().rawName(name).appId(match.appId())
            .baseAppId(match.baseAppId()).matchStatus(match.status()).build());
      }
      toSave.add(transaction);
    }

    transactionRepository.saveAll(toSave);
    return new PurchaseImportResponse(toSave.size(), duplicates, List.copyOf(unmatched), List.copyOf(unsupported));
  }

  // 가져온 구매내역을 모두 지운다 (다시 가져오기 전 초기화)
  @Transactional
  public int deleteAll(Long userId) {
    loadUser(userId);
    transactionRepository.deleteItemsByUserId(userId);
    return transactionRepository.deleteTransactionsByUserId(userId);
  }

  static Integer toKrw(BigDecimal amount, BigDecimal rateToKrw) {
    return amount.multiply(rateToKrw).setScale(0, RoundingMode.HALF_UP).intValueExact();
  }

  private User loadUser(Long userId) {
    if (userId == null) {
      throw new AuthenticationRequiredException();
    }
    return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
  }
}
