package org.ict.final_project_back_end.domain.aiPc;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import com.fasterxml.jackson.databind.ObjectMapper;import java.net.URI;import java.util.*;
import org.ict.final_project_back_end.domain.aiPc.dto.PcFps;
class PcEvidenceIntegrityTest {
 @Test void importedReviewKeepsSourcesAndCannotBecomeOwnPcMeasurement()throws Exception{
  try(var stream=getClass().getResourceAsStream("/pc-benchmarks/review-reference.json")){
   var rows=new ObjectMapper().readValue(stream,PcFps.Sample[].class);assertTrue(rows.length>1000);
   for(var row:rows){assertEquals(Boolean.FALSE,row.projectionEligible());assertTrue(row.averageFps()>0&&Double.isFinite(row.averageFps()));assertTrue(Set.of("FHD","QHD","4K").contains(row.resolution()));assertEquals("www.computerbase.de",URI.create(row.sourceUrl()).getHost());assertFalse(row.measuredAt().isBlank());assertFalse(row.preset().isBlank());}
  }
 }
 @Test void publisherTargetsAreNotPersonalMeasurements()throws Exception{
  try(var stream=getClass().getResourceAsStream("/pc-benchmarks/publisher-targets.json")){
   var rows=new ObjectMapper().readValue(stream,PcFps.PublisherTarget[].class);assertTrue(rows.length>0);
   for(var row:rows){assertNotNull(row.appId());assertTrue(row.fps()>0);assertEquals("https",URI.create(row.sourceUrl()).getScheme());}
  }
 }
}
