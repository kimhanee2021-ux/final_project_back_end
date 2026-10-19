package org.ict.final_project_back_end.domain.aiPc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.ict.final_project_back_end.domain.aiPc.dto.PcSpecs;
import org.ict.final_project_back_end.domain.aiPc.service.PcFpsEvidence;
class PcFpsEvidenceTest {
 private PcSpecs pc(String gpu){return new PcSpecs("Intel Core i7-13700","",gpu,"",32.0,"3200 MT/s",1000.0,500.0,"");}
 @Test void keepsPublishedValueAndDifferentTestCpuVisible(){var data=PcFpsEvidence.find(pc("Intel UHD Graphics 770"),1245620,"FHD");assertEquals("REFERENCE_ONLY",data.status());assertEquals(20.6,data.samples().getFirst().averageFps());assertEquals("Intel Core i9-13900HX",data.samples().getFirst().cpu());assertNull(data.lowerFps());assertNull(data.upperFps());}
 @Test void neverScalesFhdToUnknownQhdOrUhd(){for(String resolution:new String[]{"QHD","4K"}){var data=PcFpsEvidence.find(pc("Intel UHD Graphics 770"),1245620,resolution);assertEquals("NO_DATA",data.status());assertTrue(data.samples().isEmpty());}}
 @Test void doesNotMatchOtherGpuOrOtherGame(){assertEquals("NO_DATA",PcFpsEvidence.find(pc("Intel UHD Graphics 7700"),1245620,"FHD").status());assertEquals("NO_DATA",PcFpsEvidence.find(pc("Intel UHD Graphics 770"),578080,"FHD").status());}
 @Test void keepsDifferentPresetsSeparateWithoutInventingRange(){var data=PcFpsEvidence.find(pc("Intel(R) UHD Graphics 770"),570,"FHD");assertEquals(2,data.samples().size());assertNotEquals(data.samples().get(0).preset(),data.samples().get(1).preset());assertNull(data.lowerFps());assertNull(data.upperFps());}
 @Test void aiContextIncludesConditionsButNotFpsNumbers()throws Exception{
  String payload=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(PcFpsEvidence.explanationContext(pc("Intel UHD Graphics 770"),570));
  assertTrue(payload.contains("REFERENCE_PROJECTION"));assertTrue(payload.contains("DDR5-4800"));assertTrue(payload.contains("NO_DATA"));
  assertFalse(payload.contains("averageFps"));assertFalse(payload.contains("44.2"));assertFalse(payload.contains("40.8"));
  assertEquals(44.0,PcFpsEvidence.find(pc("Intel UHD Graphics 770"),570,"FHD").projection().averageFps());
 }
 @Test void cs2DifferentCpuRemainsReferenceOnly(){var data=PcFpsEvidence.find(pc("Intel UHD Graphics 770"),730,"FHD");assertEquals("REFERENCE_ONLY",data.status());assertEquals(3,data.samples().size());assertNull(data.projection());assertEquals(17.6,data.samples().get(2).averageFps());}
 @Test void lowPresetIsNeverRelabeledHigh(){var data=PcFpsEvidence.find(pc("Intel UHD Graphics 770"),1286680,"FHD");assertTrue(data.preset().contains("Low"));assertFalse(data.preset().contains("High"));assertEquals(16.0,data.projection().averageFps());}
}
