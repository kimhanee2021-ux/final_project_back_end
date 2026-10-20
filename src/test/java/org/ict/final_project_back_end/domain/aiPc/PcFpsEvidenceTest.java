package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import org.ict.final_project_back_end.domain.aiPc.dto.PcSpecs;
import org.ict.final_project_back_end.domain.aiPc.service.PcFpsEvidence;
import org.junit.jupiter.api.Test;

class PcFpsEvidenceTest {
  @Test
  void pubgDesktopMeasurementsKeepPresetAndCannotBecomePersonalProjection() {
    var same =
        new PcSpecs(
            "AMD Ryzen 9 7950X3D",
            "",
            "NVIDIA GeForce RTX 3060",
            "",
            48.0,
            "DDR5-6000",
            1000.0,
            500.0,
            "");
    var data = PcFpsEvidence.find(same, 578080, "FHD");
    assertEquals("REFERENCE_ONLY", data.status());
    assertNull(data.projection());
    assertTrue(
        data.samples().stream()
            .anyMatch(
                s -> s.averageFps() == 128.1 && s.preset().equals("Ultra; DX11 enhanced; TAA")));
    assertTrue(
        data.samples().stream()
            .allMatch(
                s ->
                    Boolean.FALSE.equals(s.projectionEligible())
                        && s.measuredAt().contains("2024-04-23")));
    assertEquals(
        "NO_DATA",
        PcFpsEvidence.find(pc("NVIDIA GeForce RTX 3060 Laptop GPU"), 578080, "FHD").status());
  }

  @Test
  void importedReviewCannotBecomeCurrentPcProjection() {
    var same =
        new PcSpecs(
            "AMD Ryzen 7 9800X3D", "", "NVIDIA GeForce RTX 4090", "", 64.0, "", 1000.0, 500.0, "");
    var data = PcFpsEvidence.find(same, 2358720, "QHD");
    assertEquals("REFERENCE_ONLY", data.status());
    assertFalse(data.samples().isEmpty());
    assertNull(data.projection());
    assertTrue(data.samples().stream().allMatch(x -> Boolean.FALSE.equals(x.projectionEligible())));
  }

  @Test
  void publisherTargetDoesNotBecomeMeasuredFps() {
    var data = PcFpsEvidence.find(pc("Unknown GPU"), 2215430, "FHD");
    assertEquals("NO_DATA", data.status());
    assertTrue(data.samples().isEmpty());
    assertEquals(60, data.publisherTargets().getFirst().fps());
    assertEquals("Medium", data.publisherTargets().getFirst().preset());
  }

  @Test
  void secondPublisherKeepsNativeMeasuredValueAndDifferentCpu() {
    var cyber = PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 1091500, "FHD");
    assertEquals("REFERENCE_ONLY", cyber.status());
    assertEquals(11.5, cyber.samples().getFirst().averageFps());
    assertEquals("Intel Core i9-12900K", cyber.samples().getFirst().cpu());
    assertTrue(cyber.samples().getFirst().renderingMode().contains("FSR 끔"));
    assertEquals(
        10.7,
        PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 534380, "FHD")
            .samples()
            .getFirst()
            .averageFps());
    assertEquals(
        "NO_DATA", PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 1091500, "QHD").status());
  }

  private PcSpecs pc(String gpu) {
    return new PcSpecs("Intel Core i7-13700", "", gpu, "", 32.0, "3200 MT/s", 1000.0, 500.0, "");
  }

  @Test
  void keepsPublishedValueAndDifferentTestCpuVisible() {
    var data = PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 1245620, "FHD");
    assertEquals("REFERENCE_ONLY", data.status());
    assertEquals(20.6, data.samples().getFirst().averageFps());
    assertEquals("Intel Core i9-13900HX", data.samples().getFirst().cpu());
    assertNull(data.lowerFps());
    assertNull(data.upperFps());
  }

  @Test
  void neverScalesFhdToUnknownQhdOrUhd() {
    for (String resolution : new String[] {"QHD", "4K"}) {
      var data = PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 1245620, resolution);
      assertEquals("NO_DATA", data.status());
      assertTrue(data.samples().isEmpty());
    }
  }

  @Test
  void doesNotMatchOtherGpuOrOtherGame() {
    assertEquals(
        "NO_DATA", PcFpsEvidence.find(pc("Intel UHD Graphics 7700"), 1245620, "FHD").status());
    assertEquals(
        "NO_DATA", PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 578080, "FHD").status());
  }

  @Test
  void keepsDifferentPresetsSeparateWithoutInventingRange() {
    var data = PcFpsEvidence.find(pc("Intel(R) UHD Graphics 770"), 570, "FHD");
    assertEquals(2, data.samples().size());
    assertNotEquals(data.samples().get(0).preset(), data.samples().get(1).preset());
    assertNull(data.lowerFps());
    assertNull(data.upperFps());
  }

  @Test
  void aiContextIncludesPublishedValuesWithExternalMeasurementConditions() throws Exception {
    String payload =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(
                PcFpsEvidence.explanationContext(pc("Intel UHD Graphics 770"), 570));
    assertTrue(payload.contains("REFERENCE_PROJECTION"));
    assertTrue(payload.contains("DDR5-4800"));
    assertTrue(payload.contains("NO_DATA"));
    assertTrue(payload.contains("averageFps"));
    assertTrue(payload.contains("44.2"));
    assertTrue(payload.contains("40.8"));
    assertTrue(payload.contains("renderingMode"));
    assertEquals(
        44.0,
        PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 570, "FHD").projection().averageFps());
  }

  @Test
  void cs2DifferentCpuRemainsReferenceOnly() {
    var data = PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 730, "FHD");
    assertEquals("REFERENCE_ONLY", data.status());
    assertEquals(3, data.samples().size());
    assertNull(data.projection());
    assertEquals(17.6, data.samples().get(2).averageFps());
  }

  @Test
  void lowPresetIsNeverRelabeledHigh() {
    var data = PcFpsEvidence.find(pc("Intel UHD Graphics 770"), 1286680, "FHD");
    assertTrue(data.preset().contains("Low"));
    assertFalse(data.preset().contains("High"));
    assertEquals(16.0, data.projection().averageFps());
  }

  @Test
  void vramVariantAndLaptopGpuCannotBorrowDesktopReference() {
    var differentMemory =
        new PcSpecs(
            "Ryzen 7 9800X3D", "", "NVIDIA GeForce RTX 4090", "8 GB", 64.0, "", 1000.0, 500.0, "");
    assertEquals("NO_DATA", PcFpsEvidence.find(differentMemory, 2358720, "QHD").status());
    assertEquals(
        "NO_DATA",
        PcFpsEvidence.find(pc("NVIDIA GeForce RTX 4090 Laptop GPU"), 2358720, "QHD").status());
  }
}
