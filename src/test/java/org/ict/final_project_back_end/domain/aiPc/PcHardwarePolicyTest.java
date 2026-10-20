package org.ict.final_project_back_end.domain.aiPc;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import org.ict.final_project_back_end.domain.aiPc.dto.*;import org.ict.final_project_back_end.domain.aiPc.service.*;import java.util.*;
class PcHardwarePolicyTest {
 @Test void missingCompatibilityDoesNotEraseIdentifiedUpgradeDirection(){
  var result=PcHardwarePolicy.advice(pc("Intel UHD Graphics 770","UNKNOWN"),List.of(new PcComponentAdvice("GPU","UPGRADE","내장 그래픽이 게임 성능의 제한 요인","",null)));
  assertEquals("UPGRADE",result.getFirst().status());assertNull(result.getFirst().offer());
  assertTrue(result.getFirst().compatibilityNote().contains("데스크톱"));
 }
 PcSpecs pc(String gpu,String type){return new PcSpecs("CPU","",gpu,"",32.0,"5600 MT/s",1000.0,500.0,"",type);}
 @Test void multipleGpuNamesRequireChoice(){assertTrue(PcHardwarePolicy.ambiguousGpu("NVIDIA GeForce RTX 5060 Laptop GPU, AMD Radeon 780M Graphics"));assertFalse(PcHardwarePolicy.ambiguousGpu("NVIDIA GeForce RTX 5060 Laptop GPU"));}
 @Test void laptopAndUnknownDoNotGetDesktopReplacement(){assertFalse(PcHardwarePolicy.blockedProductReason(pc("RTX 5060 Laptop GPU","UNKNOWN"),"GPU").isBlank());assertFalse(PcHardwarePolicy.blockedProductReason(pc("RTX 5060","UNKNOWN"),"CPU").isBlank());assertTrue(PcHardwarePolicy.blockedProductReason(pc("RTX 5060","DESKTOP"),"GPU").isBlank());}
 @Test void freeStorageDoesNotCreateUpgradeNeed(){var result=PcHardwarePolicy.advice(pc("RTX5060","DESKTOP"),List.of(new PcComponentAdvice("SSD","UPGRADE","공간 확장","슬롯 확인",null)));assertEquals("KEEP",result.getFirst().status());assertNull(result.getFirst().offer());}
 @Test void unknownSlotAndRamFormFactorBlockProduct(){assertFalse(PcHardwarePolicy.blockedProductReason(pc("RTX5060","LAPTOP"),"RAM").isBlank());assertFalse(PcHardwarePolicy.blockedProductReason(pc("RTX5060","LAPTOP"),"SSD").isBlank());}
 @Test void unsupportedMandatoryUpgradeBecomesConditional(){var result=PcHardwarePolicy.advice(pc("RTX5060","DESKTOP"),List.of(new PcComponentAdvice("GPU","UPGRADE","외장 그래픽 추가가 필수입니다","장착 확인",null)));assertEquals("CHECK",result.getFirst().status());assertNull(result.getFirst().offer());}
}
