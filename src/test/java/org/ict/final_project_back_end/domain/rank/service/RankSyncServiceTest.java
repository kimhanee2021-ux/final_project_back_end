package org.ict.final_project_back_end.domain.rank.service;

import org.ict.final_project_back_end.domain.rank.client.SteamRankClient;
import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot;
import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot.Product;
import org.ict.final_project_back_end.domain.rank.exception.SteamRankFetchException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankSyncServiceTest {
  private final SteamRankClient client = mock(SteamRankClient.class);
  private final RankSnapshotWriter writer = mock(RankSnapshotWriter.class);
  private static final LocalDate WEEK = LocalDate.of(2026, 10, 13);

  @Test
  void previewPinsBothCountriesToOneWeekAndNeverWrites() {
    var kr = snapshot("KR", WEEK);var us = snapshot("US", WEEK);
    when(client.fetchWeeklyRank("KR", null)).thenReturn(kr);
    when(client.fetchWeeklyRank("US", WEEK)).thenReturn(us);
    assertEquals(List.of(kr,us), new RankSyncService(client,writer,false).previewWeeklyRanks(null));
    verify(client).fetchWeeklyRank("KR", null);verify(client).fetchWeeklyRank("US", WEEK);
    verifyNoInteractions(writer);
  }

  @Test
  void validatesBothBeforeOneWriterCall() {
    var kr = snapshot("KR", WEEK);var us = snapshot("US", WEEK);
    when(client.fetchWeeklyRank("KR", WEEK)).thenReturn(kr);
    when(client.fetchWeeklyRank("US", WEEK)).thenReturn(us);
    new RankSyncService(client,writer,true).syncWeeklyRanks(WEEK);
    var order = inOrder(client,writer);
    order.verify(client).fetchWeeklyRank("KR", WEEK);
    order.verify(client).fetchWeeklyRank("US", WEEK);
    order.verify(writer).savePair(List.of(kr,us));
    verifyNoMoreInteractions(writer);
  }

  @Test
  void failureInEitherCountryOrMismatchedWeekNeverWrites() {
    var service = new RankSyncService(client,writer,true);
    when(client.fetchWeeklyRank("KR", WEEK)).thenThrow(new SteamRankFetchException("failed"));
    assertThrows(SteamRankFetchException.class, () -> service.syncWeeklyRanks(WEEK));
    reset(client);
    when(client.fetchWeeklyRank("KR", WEEK)).thenReturn(snapshot("KR", WEEK));
    when(client.fetchWeeklyRank("US", WEEK)).thenThrow(new SteamRankFetchException("failed"));
    assertThrows(SteamRankFetchException.class, () -> service.syncWeeklyRanks(WEEK));
    reset(client);
    when(client.fetchWeeklyRank("KR", WEEK)).thenReturn(snapshot("KR", WEEK));
    when(client.fetchWeeklyRank("US", WEEK)).thenReturn(snapshot("US", WEEK.minusDays(7)));
    assertThrows(IllegalArgumentException.class, () -> service.syncWeeklyRanks(WEEK));
    verifyNoInteractions(writer);
  }

  static SteamWeeklySnapshot snapshot(String country, LocalDate week) {
    return new SteamWeeklySnapshot(country,week,Instant.parse("2026-10-21T05:00:00Z"),
        IntStream.rangeClosed(1,10).mapToObj(i -> new Product(i,1000L+i,"Game " + i,i,2,null)).toList());
  }
}
