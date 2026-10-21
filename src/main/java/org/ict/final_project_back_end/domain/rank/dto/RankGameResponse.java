package org.ict.final_project_back_end.domain.rank.dto;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankEntry;

// 순위는 저장된 주간 스냅샷, 부가 정보(가격/장르/사양 등)는 현재 games 메타데이터다.
public record RankGameResponse(Long appId, String name, Integer steamRank, Integer gameRank,
    Integer lastWeekRank, Integer consecutiveWeeks, String genre, String priceInfo,
    String thumbnailUrl, String minSpecRawText, String recSpecRawText,
    String imageUrlSnapshot, String rankChangeKind) {

  public static RankGameResponse from(WeeklyRankEntry entry, Game game) {
    String name = game != null && game.getName() != null && !game.getName().isBlank()
        ? game.getName() : entry.getGameNameSnapshot();
    return new RankGameResponse(entry.getAppId(), name, entry.getSteamRank(), entry.getGameRank(),
        entry.getLastWeekRank(), entry.getConsecutiveWeeks(), game == null ? null : game.getGenre(),
        game == null ? null : game.getPriceInfo(), game == null ? null : game.getThumbnailUrl(),
        game == null ? null : game.getMinSpecRawText(), game == null ? null : game.getRecSpecRawText(),
        entry.getImageUrlSnapshot(), entry.getRankChangeKind());
  }
}
