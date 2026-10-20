package org.ict.final_project_back_end.domain.rank;

import org.ict.final_project_back_end.domain.rank.client.SteamRankClient;
import org.ict.final_project_back_end.domain.rank.service.RankSyncService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RankCollectionDisabledTest {

  @Test
  void clientFailsExplicitlyInsteadOfReturningUnverifiedData() {
    var exception = assertThrows(UnsupportedOperationException.class,
        () -> new SteamRankClient().fetchWeeklyRank("KR", LocalDate.of(2026, 10, 6)));
    assertTrue(exception.getMessage().contains("미구현"));
  }

  @Test
  void syncFailsExplicitlyWithoutAnyDatabaseOrNetworkDependency() {
    var exception = assertThrows(UnsupportedOperationException.class,
        () -> new RankSyncService().syncWeeklyRank("KR", LocalDate.of(2026, 10, 6)));
    assertTrue(exception.getMessage().contains("동기화할 수 없습니다"));
  }
}
