package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.ict.final_project_back_end.domain.aiPc.entity.PcPartPriceSnapshot;
import org.ict.final_project_back_end.domain.aiPc.repository.PcPartPriceRepository;
import org.ict.final_project_back_end.domain.aiPc.service.*;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

class PcPartsCacheTest {
  @Test
  void diagnosisDoesNotWaitForPriceRefreshWhenCacheIsMissing() {
    var prices = mock(PcPartPriceRepository.class);
    var users = mock(UserRepository.class);
    when(users.existsById(1L)).thenReturn(true);
    when(prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(anyLong(), anyString()))
        .thenReturn(List.of());
    assertTrue(new PcPartsService(prices, users, new PcPriceSourceClient()).cachedOffers(1L).isEmpty());
    verify(prices, never()).save(any());
  }

  @Test
  void stalePriceCannotAppearAsCurrentOffer() {
    var prices = mock(PcPartPriceRepository.class);
    var users = mock(UserRepository.class);
    when(users.existsById(1L)).thenReturn(true);
    when(prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(anyLong(), anyString()))
        .thenReturn(
            List.of(
                new PcPartPriceSnapshot(
                    74118179L,
                    "DANAWA",
                    100000L,
                    "OBSERVED",
                    "old",
                    Instant.now().minusSeconds(22000).toString())));
    assertTrue(new PcPartsService(prices, users, new PcPriceSourceClient()).cachedOffers(1L).isEmpty());
    verify(prices, never()).save(any());
  }
}
