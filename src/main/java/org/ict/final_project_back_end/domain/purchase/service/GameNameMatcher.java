package org.ict.final_project_back_end.domain.purchase.service;

import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.purchase.client.SteamAppInfoClient;
import org.ict.final_project_back_end.domain.purchase.entity.PurchaseItem;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// 구매내역의 상품 이름을 게임 appId에 연결한다. 순서:
//   1) 구매내역에 appId가 적혀 있으면(게임 내 구매) 그대로
//   2) 내 보유 게임과 이름이 같으면 그 게임
//   3) 보유 게임 이름 + 구분자(": ", " - ")로 시작하면 DLC 후보 -> 스팀에서 DLC이고 본편이 그 게임인지 확인되면 본편에 연결
//      (이름 앞부분만 같은 속편/시리즈는 묶지 않는다: Hollow Knight: Silksong은 별도 게임)
//   4) 게임 목록(games)에 같은 이름이 있으면 그 게임 (이름만 같은 다른 게임이 여럿이면 추천 수가 가장 많은 게임)
//   5) 스팀 상점 검색으로 이름이 정확히 같은 상품을 찾음 (보유 게임의 DLC면 본편에 연결)
//   6) 못 찾으면 UNMATCHED
// 스팀 호출(3, 5)은 가져오기 한 번에 쓸 수 있는 횟수(Budget)를 넘지 않는다.
@Component
public class GameNameMatcher {

  public record Match(Long appId, Long baseAppId, String status) {
    static Match matched(Long appId) {
      return new Match(appId, null, PurchaseItem.MATCHED);
    }

    static Match dlc(Long appId, Long baseAppId) {
      return new Match(appId, baseAppId, PurchaseItem.DLC);
    }

    static final Match UNMATCHED = new Match(null, null, PurchaseItem.UNMATCHED);
  }

  // 가져오기 한 번 동안 스팀에 물어볼 수 있는 남은 횟수
  public static final class Budget {
    private int remaining;

    public Budget(int remaining) {
      this.remaining = remaining;
    }

    boolean take() {
      if (remaining <= 0) {
        return false;
      }
      remaining--;
      return true;
    }
  }

  private static final Pattern MARKS = Pattern.compile("[™®©]");
  private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
  // 본편 이름 뒤에 DLC 이름이 붙을 때 쓰는 구분자
  // 보유 게임 이름이 끝에 붙을 때 바로 앞에 와야 하는 글자 (공백 또는 구분자)
  private static final Pattern SUFFIX_BOUNDARY = Pattern.compile("[\\s:：\\-–—]");
  private static final Pattern DLC_SEPARATOR = Pattern.compile("^\\s*(:|-|–|—)\\s*\\S");

  private final GameRepository gameRepository;
  private final SteamAppInfoClient steamAppInfoClient;

  public GameNameMatcher(GameRepository gameRepository, SteamAppInfoClient steamAppInfoClient) {
    this.gameRepository = gameRepository;
    this.steamAppInfoClient = steamAppInfoClient;
  }

  // 보유 게임 목록을 매칭에 쓰는 "이름(원문) -> appId" 표로 바꾼다. 같은 이름이 여러 개면 처음 것.
  public static Map<String, Long> libraryByName(List<LibraryGame> games) {
    Map<String, Long> library = new HashMap<>();
    for (LibraryGame game : games) {
      library.putIfAbsent(game.getName(), game.getAppId());
    }
    return library;
  }

  // library: 내 보유 게임 이름(원문) -> appId
  public Match match(String rawName, Long hintedAppId, Map<String, Long> library, Budget budget) {
    if (hintedAppId != null) {
      return Match.matched(hintedAppId);
    }
    String normalized = normalize(rawName);
    for (Map.Entry<String, Long> owned : library.entrySet()) {
      if (normalize(owned.getKey()).equals(normalized)) {
        return Match.matched(owned.getValue());
      }
    }
    Long baseCandidate = dlcBaseCandidate(rawName, library);
    if (baseCandidate != null) {
      return confirmDlc(rawName, baseCandidate, budget);
    }
    Long suffixMatch = ownedNameAtEnd(rawName, library);
    if (suffixMatch != null) {
      return Match.matched(suffixMatch);
    }
    Long catalogAppId = gameRepository.findFirstByNameIgnoreCaseOrderByRecommendationsDesc(rawName.trim())
        .map(g -> g.getAppId()).orElse(null);
    if (catalogAppId != null) {
      return Match.matched(catalogAppId);
    }
    if (!budget.take()) {
      return Match.UNMATCHED;
    }
    Long found = searchWithSeparatorFallback(rawName, budget);
    return found == null ? Match.UNMATCHED : Match.matched(found);
  }

  // 스팀 검색은 "Risk of Rain 2 - Survivors of the Void"처럼 " - " 표기로는 못 찾고 ": " 표기로만 찾는 경우가 있다
  private Long searchWithSeparatorFallback(String rawName, Budget budget) {
    Long found = steamAppInfoClient.searchExactAppId(rawName);
    String alternative = colonVariant(rawName);
    if (found == null && alternative != null && budget.take()) {
      found = steamAppInfoClient.searchExactAppId(alternative);
    }
    return found;
  }

  // 첫 번째 " - " 구분자를 ": "로 바꾼 이름. 바꿀 게 없으면 null.
  static String colonVariant(String rawName) {
    int index = rawName.indexOf(" - ");
    return index < 0 ? null : rawName.substring(0, index) + ": " + rawName.substring(index + 3);
  }

  private Match confirmDlc(String rawName, Long baseCandidate, Budget budget) {
    if (!budget.take()) {
      return Match.UNMATCHED;
    }
    Long dlcAppId = searchWithSeparatorFallback(rawName, budget);
    if (dlcAppId == null || !budget.take()) {
      return Match.UNMATCHED;
    }
    SteamAppInfoClient.AppInfo info = steamAppInfoClient.fetchInfo(dlcAppId);
    if (info != null && info.isDlcOf(baseCandidate)) {
      return Match.dlc(dlcAppId, baseCandidate);
    }
    // DLC가 아니면(속편 등) 그 상품 자체로 연결한다
    return info != null && !"dlc".equals(info.type()) ? Match.matched(dlcAppId) : Match.UNMATCHED;
  }

  // 대소문자·기호·공백 차이를 없앤 비교용 이름 (한글·한자 등 문자는 유지)
  public static String normalize(String name) {
    if (name == null) {
      return "";
    }
    // ™ 같은 기호는 NFKC가 "TM" 글자로 바꿔 버리므로 먼저 지운다 (DARK SOULS™ III == DARK SOULS III)
    String text = MARKS.matcher(name).replaceAll("");
    text = Normalizer.normalize(text, Normalizer.Form.NFKC);
    return NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
  }

  // 라이선스 페이지 이름 꾸밈말: "Don't Starve Together Gift", "Aimlabs - Free", "DARK SOULS II: Bundle (DX9)"의 (DX9)
  private static final Pattern LICENSE_SUFFIX = Pattern.compile("(?i)(\\s*-\\s*free|\\s+free|\\s+gift|\\s*\\([^)]*\\))\\s*$");

  // 라이선스 이름을 내 보유 게임 appId에 연결한다 (금액이 없어 스팀 확인 없이 이름만 본다). 못 찾으면 null.
  //   1) 이름이 같음  2) 꾸밈말(Gift, - Free, (DX9))을 뗀 이름이 같음  3) "보유 게임 이름 + 구분자"로 시작(번들·DLC 패키지)
  public static Long matchOwnedLicense(String rawName, Map<String, Long> library) {
    String cleaned = rawName;
    for (String previous = null; !cleaned.equals(previous); ) {
      previous = cleaned;
      cleaned = LICENSE_SUFFIX.matcher(cleaned).replaceAll("");
    }
    for (String candidate : new String[]{rawName, cleaned}) {
      String normalized = normalize(candidate);
      for (Map.Entry<String, Long> owned : library.entrySet()) {
        if (normalize(owned.getKey()).equals(normalized)) {
          return owned.getValue();
        }
      }
    }
    Long base = dlcBaseCandidate(rawName, library);
    return base != null ? base : ownedNameAtEnd(rawName, library);
  }

  // 이름 앞에 다른 언어 제목이 붙은 경우: "清零计划2：天启派对 Project Zero 2: Apocalypse Party" -> 보유 게임 "Apocalypse Party".
  // 짧은 이름이 우연히 겹치지 않게, 보유 게임 이름이 8자 이상이고 바로 앞이 공백·구분자일 때만 인정한다.
  public static Long ownedNameAtEnd(String rawName, Map<String, Long> library) {
    String lower = MARKS.matcher(rawName).replaceAll("").toLowerCase(Locale.ROOT).trim();
    String bestName = null;
    Long best = null;
    for (Map.Entry<String, Long> owned : library.entrySet()) {
      String name = MARKS.matcher(owned.getKey()).replaceAll("").toLowerCase(Locale.ROOT).trim();
      if (name.length() >= 8 && lower.length() > name.length() && lower.endsWith(name)
          && SUFFIX_BOUNDARY.matcher(lower.substring(lower.length() - name.length() - 1, lower.length() - name.length())).matches()
          && (bestName == null || name.length() > bestName.length())) {
        bestName = name;
        best = owned.getValue();
      }
    }
    return best;
  }

  // "보유 게임 이름 + 구분자 + 무언가"로 시작하면 그 보유 게임을 DLC 본편 후보로 돌려준다. 여러 개면 가장 긴 이름.
  public static Long dlcBaseCandidate(String rawName, Map<String, Long> library) {
    String lower = MARKS.matcher(rawName).replaceAll("").toLowerCase(Locale.ROOT).trim();
    String bestName = null;
    Long best = null;
    for (Map.Entry<String, Long> owned : library.entrySet()) {
      String base = MARKS.matcher(owned.getKey()).replaceAll("").toLowerCase(Locale.ROOT).trim();
      if (!base.isEmpty() && lower.startsWith(base) && DLC_SEPARATOR.matcher(lower.substring(base.length())).find()
          && (bestName == null || base.length() > bestName.length())) {
        bestName = base;
        best = owned.getValue();
      }
    }
    return best;
  }
}
