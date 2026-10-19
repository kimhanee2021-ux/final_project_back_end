package org.ict.final_project_back_end.domain.aiPc;
import org.ict.final_project_back_end.domain.aiPc.dto.PcSpecs;
import org.ict.final_project_back_end.domain.aiPc.service.PcRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcRulesTest {
 private PcSpecs pc(double ram,double free){return new PcSpecs("Intel Core i5-8400","6 cores","NVIDIA GTX 1060","6GB",ram,"3200",500.0,free,"Windows 10");}
 private final String minimum="OS: Windows 10 Processor: Intel Core i5-8400 Memory: 12 GB RAM Graphics: NVIDIA GTX 1060 Storage: 60 GB available space";
 @Test void exactSourceModelsAndEnoughCapacityCanPass(){assertEquals("O",PcRules.compare(pc(16,100),minimum).grade());}
 @Test void insufficientMemoryNeverPasses(){var result=PcRules.compare(pc(8,100),minimum);assertEquals("X",result.grade());assertTrue(result.checks().stream().anyMatch(s->s.contains("RAM 부족")));}
 @Test void fasterLookingModelNumberDoesNotProveCompatibility(){var p=new PcSpecs("Intel Core i7-13700","","Intel UHD Graphics 770","",32.0,"",500.0,100.0,"Windows 10");assertEquals("X",PcRules.compare(p,minimum).grade());}
 @Test void unknownOrMissingSourceNeverPasses(){assertEquals("X",PcRules.compare(pc(32,100),"").grade());}
 @Test void mbStorageIsConverted(){var r=PcRules.compare(pc(32,1),minimum.replace("60 GB available space","500 MB available space"));assertEquals("O",r.grade());}
 @Test void missingFreeSpaceCannotBeInvented(){var p=new PcSpecs("Intel Core i5-8400","","NVIDIA GTX 1060","",16.0,"",500.0,null,"Windows 10");assertEquals("X",PcRules.compare(p,minimum).grade());}
 @Test void manufacturerNameAloneDoesNotProveModel(){var p=new PcSpecs("Intel","","NVIDIA","",32.0,"",500.0,100.0,"Windows 10");assertEquals("X",PcRules.compare(p,minimum).grade());}
 @Test void modelSuffixCannotMatchDifferentModel(){assertEquals("X",PcRules.compare(pc(32,100),minimum.replace("i5-8400","i5-8400T")).grade());}
 @Test void extraApiRequirementsMustBeVerified(){assertEquals("X",PcRules.compare(pc(32,100),minimum+" DirectX: Version 12").grade());}
}
