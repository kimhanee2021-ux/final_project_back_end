package org.ict.final_project_back_end.domain.aipc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.ict.final_project_back_end.domain.aipc.dto.*;
import org.ict.final_project_back_end.domain.aipc.rule.PcFpsProjection;
import org.junit.jupiter.api.Test;

class PcFpsProjectionTest {
  private PcSpecs pc(String cpu, Double ram) {
    return new PcSpecs(cpu, "", "Intel UHD Graphics 770", "", ram, "3200 MT/s", null, null, "");
  }

  private PcFps.Sample sample(String cpu, String preset, double fps, String rig) {
    return new PcFps.Sample(
        rig + preset,
        570,
        "Intel UHD Graphics 770",
        cpu,
        16.0,
        "DDR5-4800",
        "FHD",
        preset,
        fps,
        "unknown",
        "unknown",
        "2023",
        "https://www.notebookcheck.net/test",
        rig);
  }

  @Test
  void usesOnlySameCpuGpuWithoutPerformanceRanking() {
    assertNull(
        PcFpsProjection.project(
            pc("Intel Core i7-13700", 32.0),
            List.of(sample("Intel Core i9-13900HX", "FHD high", 20.6, "other"))));
  }

  @Test
  void missingOrInsufficientRamCannotProject() {
    for (Double ram : new Double[] {null, 8.0})
      assertNull(
          PcFpsProjection.project(
              pc("Intel Core i7-13700", ram),
              List.of(sample("Intel Core i7-13700", "FHD high", 44.2, "a"))));
  }

  @Test
  void doesNotMixHighAndUltraPresets() {
    var projection =
        PcFpsProjection.project(
            pc("Intel Core i7-13700", 32.0),
            List.of(
                sample("Intel Core i7-13700", "FHD high preset", 44.2, "a"),
                sample("Intel Core i7-13700", "FHD ultra preset", 40.8, "a")));
    assertEquals(44, projection.averageFps());
    assertEquals("FHD high preset", projection.preset());
    assertEquals(1, projection.testSystems());
  }

  @Test
  void duplicatePublicationDoesNotIncreaseSystemCount() {
    var sample = sample("Intel Core i7-13700", "FHD high", 44.2, "a");
    assertEquals(
        1,
        PcFpsProjection.project(pc("Intel Core i7-13700", 32.0), List.of(sample, sample))
            .testSystems());
  }

  @Test
  void invalidNumbersDoNotEnterProjection() {
    for (double fps : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
      assertNull(
          PcFpsProjection.project(
              pc("Intel Core i7-13700", 32.0),
              List.of(sample("Intel Core i7-13700", "FHD high", fps, "a"))));
  }
}
