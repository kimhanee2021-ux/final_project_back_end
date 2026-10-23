package org.ict.final_project_back_end.domain.aipc;

import static org.junit.jupiter.api.Assertions.*;

import org.ict.final_project_back_end.domain.aipc.client.GroqPcQuota;
import org.junit.jupiter.api.Test;

class GroqPcQuotaTest {
  @Test
  void minuteWindowIncludesOnlySafeNumericDetails() {
    String result =
        GroqPcQuota.describe(
            "secret-org tokens per minute (TPM): Limit 7000, Used 3272, Requested 4992.");
    assertTrue(result.contains("분당 토큰"));
    assertTrue(result.contains("이번 요청 4992"));
    assertFalse(result.contains("secret-org"));
    assertTrue(result.contains("하루 한도 소진과는 달라요"));
  }

  @Test
  void dayWindowIsNeverExplainedAsMinuteRecovery() {
    String result =
        GroqPcQuota.describe("tokens per day (TPD) Limit 200000 Used 200000 Requested 500");
    assertTrue(result.contains("일일 토큰"));
    assertFalse(result.contains("하루 한도 소진과는 달라요"));
  }
}
