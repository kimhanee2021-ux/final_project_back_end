package org.ict.final_project_back_end.domain.aipc;

import static org.junit.jupiter.api.Assertions.*;

import org.ict.final_project_back_end.domain.aicommon.exception.AiException;
import org.ict.final_project_back_end.domain.aipc.rule.PcStorageReading;
import org.junit.jupiter.api.Test;

class PcStorageReadingTest {
  @Test
  void computesFromRawUnitsInsteadOfWrongAiArithmetic() {
    var result = PcStorageReading.resolve("1.38 TB", "207 GB", "");
    assertEquals(1413.12, result.total());
    assertEquals(1206.12, result.free());
    assertFalse(result.warnings().isEmpty());
  }

  @Test
  void inconsistentDisplayedNumbersRemainUnknown() {
    assertNull(PcStorageReading.resolve("1 TB", "200 GB", "900 GB").free());
    assertNull(PcStorageReading.resolve("100 GB", "200 GB", "").free());
  }

  @Test
  void convertsMbAndPreservesUnknowns() {
    assertEquals(1.0, PcStorageReading.resolve("1024 MB", "", "").total());
    assertNull(PcStorageReading.resolve("", "", "").free());
  }

  @Test
  void rejectsUnrecognizedOrExcessiveUnits() {
    for (String text : new String[] {"infinity GB", "100001 GB", "32 KB"})
      assertThrows(AiException.class, () -> PcStorageReading.resolve(text, "", ""));
  }
}
