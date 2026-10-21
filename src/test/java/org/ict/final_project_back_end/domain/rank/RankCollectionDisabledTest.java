package org.ict.final_project_back_end.domain.rank;

import org.ict.final_project_back_end.domain.rank.client.SteamRankClient;
import org.ict.final_project_back_end.domain.rank.service.RankSyncService;
import org.ict.final_project_back_end.domain.rank.service.RankSnapshotWriter;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankCollectionDisabledTest {
  @Test
  void disabledSyncDoesNotCallSteamOrWriteAnything() {
    var client = mock(SteamRankClient.class);
    var writer = mock(RankSnapshotWriter.class);
    var service = new RankSyncService(client, writer, false);
    assertThrows(IllegalStateException.class, () -> service.syncWeeklyRanks(LocalDate.of(2026, 10, 13)));
    verifyNoInteractions(client, writer);
  }
}
