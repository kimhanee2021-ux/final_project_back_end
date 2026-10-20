package org.ict.final_project_back_end.domain.aiPc;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.List;import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aiPc.service.PcComponentAdviceReader;
class PcComponentAdviceReaderTest {
 @Test void missingAdviceIsCheckRatherThanAnInventedUpgrade()throws Exception{var rows=PcComponentAdviceReader.parse(new ObjectMapper().readTree("[]"),List.of());assertEquals(4,rows.size());assertTrue(rows.stream().allMatch(r->r.status().equals("CHECK")&&r.offer()==null));}
 @Test void removedCategoriesAreNotExposed()throws Exception{var rows=PcComponentAdviceReader.parse(new ObjectMapper().readTree("[{\"category\":\"파워\",\"status\":\"KEEP\",\"reason\":\"확인\",\"compatibilityNote\":\"확인\"}]"),List.of());assertTrue(rows.stream().noneMatch(r->r.category().equals("파워")));}

 @Test void unregisteredProductIsNeverUsedAsAnOffer()throws Exception{var nodes=new ObjectMapper().readTree("[{\"category\":\"GPU\",\"status\":\"UPGRADE\",\"reason\":\"옵션 목표 검토\",\"compatibilityNote\":\"공간 확인\",\"productCode\":\"999\"}]");var row=PcComponentAdviceReader.parse(nodes,List.of()).stream().filter(r->r.category().equals("GPU")).findFirst().orElseThrow();assertEquals("UPGRADE",row.status());assertNull(row.offer());}
}
