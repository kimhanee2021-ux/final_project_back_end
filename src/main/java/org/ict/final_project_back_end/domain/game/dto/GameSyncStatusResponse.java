package org.ict.final_project_back_end.domain.game.dto;

import java.time.LocalDateTime;

// 전체 게임 동기화 진행 상황. 아직 한 번도 실행되지 않았으면 lastAppId와 lastCompletedAt은 null이다.
public record GameSyncStatusResponse(
    long totalGames,
    Long lastAppId,
    boolean completed,
    LocalDateTime lastCompletedAt
) {
}
