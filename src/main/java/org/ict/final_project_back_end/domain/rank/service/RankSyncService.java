package org.ict.final_project_back_end.domain.rank.service;

import org.ict.final_project_back_end.domain.rank.client.SteamRankClient;
import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

/** Explicit calls only. No scheduler, startup runner or public write endpoint. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class RankSyncService {
  private final SteamRankClient client;
  private final RankSnapshotWriter writer;
  private final boolean syncEnabled;

  public RankSyncService(SteamRankClient client, RankSnapshotWriter writer,
      @Value("${app.rank.sync-enabled:false}") boolean syncEnabled) {
    this.client = client;
    this.writer = writer;
    this.syncEnabled = syncEnabled;
  }

  /** Fetch and validate only; never writes. null selects the latest published week. */
  public List<SteamWeeklySnapshot> previewWeeklyRanks(LocalDate weekStart) {
    var kr = client.fetchWeeklyRank("KR", weekStart);
    // Pin US to KR's returned week to avoid crossing a weekly publication boundary.
    var us = client.fetchWeeklyRank("US", kr.week());
    var snapshots = List.of(kr, us);
    RankSnapshotWriter.validatePair(snapshots);
    if (weekStart != null && !weekStart.equals(kr.week())) throw new IllegalArgumentException("Wrong requested week");
    return snapshots;
  }

  public List<SteamWeeklySnapshot> syncWeeklyRanks(LocalDate weekStart) {
    if (!syncEnabled) throw new IllegalStateException("Rank DB sync is disabled (app.rank.sync-enabled=false)");
    var snapshots = previewWeeklyRanks(weekStart);
    writer.savePair(snapshots);
    return snapshots;
  }
}
