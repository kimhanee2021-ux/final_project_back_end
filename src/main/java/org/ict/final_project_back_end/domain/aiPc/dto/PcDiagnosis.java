package org.ict.final_project_back_end.domain.aiPc.dto;
import java.util.List;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
public final class PcDiagnosis {
 private PcDiagnosis(){}
 public record Extraction(PcSpecs specs,List<String> warnings){}
 public record Request(@NotNull @Valid PcSpecs specs,@Min(1) @Max(999999999) long appId){}
 public record Resolution(String name,String explanation){}
 public record Assessment(long appId,String gameName,String grade,List<String> checks,String summary,List<Resolution> resolutions,List<String> settings,List<String> upgradeChecks,String limitation,PcGameRequirements requirements){}
}
