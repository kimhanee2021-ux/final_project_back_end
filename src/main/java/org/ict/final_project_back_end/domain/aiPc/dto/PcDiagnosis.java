package org.ict.final_project_back_end.domain.aiPc.dto;
import java.util.List;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
public final class PcDiagnosis {
 private PcDiagnosis(){}
 public record Extraction(PcSpecs specs,List<String> warnings,String aiProvider){public Extraction(PcSpecs specs,List<String> warnings){this(specs,warnings,"");}}
 public record Request(@NotNull @Valid PcSpecs specs,@Min(1) @Max(999999999) long appId,Boolean includeAi){}
 public record BatchRequest(@NotNull @Valid PcSpecs specs,@NotEmpty @Size(max=5) List<@NotNull @Min(1) @Max(999999999) Long> appIds){}
 public record OverviewRequest(@NotNull @Valid PcSpecs specs,@NotEmpty @Size(max=2000) List<@NotNull @Min(1) @Max(999999999) Long> appIds){}
 public record Overview(String overallGrade,int gameCount,List<String> overallExplanation,List<PcComponentAdvice> componentAdvice,String aiProvider){}
 public record Resolution(String name,String explanation,PcFps.Evidence fps,PcTarget target){public Resolution(String name,String explanation,PcFps.Evidence fps){this(name,explanation,fps,null);}public Resolution(String name,String explanation){this(name,explanation,null,null);}}
 public record UpgradeRecommendation(PcPart.Offer offer,String reason,String compatibilityNote){}
 public record Assessment(long appId,String gameName,String grade,List<String> checks,String summary,List<Resolution> resolutions,List<String> settings,List<String> upgradeChecks,String limitation,PcGameRequirements requirements,String explanationStatus,List<UpgradeRecommendation> recommendations,List<PcComponentAdvice> componentAdvice,String aiProvider,List<String> overallExplanation){
 public Assessment(long appId,String gameName,String grade,List<String> checks,String summary,List<Resolution> resolutions,List<String> settings,List<String> upgradeChecks,String limitation,PcGameRequirements requirements,String explanationStatus,List<UpgradeRecommendation> recommendations,List<PcComponentAdvice> componentAdvice,String aiProvider){this(appId,gameName,grade,checks,summary,resolutions,settings,upgradeChecks,limitation,requirements,explanationStatus,recommendations,componentAdvice,aiProvider,List.of());}
 public Assessment(long appId,String gameName,String grade,List<String> checks,String summary,List<Resolution> resolutions,List<String> settings,List<String> upgradeChecks,String limitation,PcGameRequirements requirements,String explanationStatus,List<UpgradeRecommendation> recommendations,List<PcComponentAdvice> componentAdvice){this(appId,gameName,grade,checks,summary,resolutions,settings,upgradeChecks,limitation,requirements,explanationStatus,recommendations,componentAdvice,"");}
 public Assessment(long appId,String gameName,String grade,List<String> checks,String summary,List<Resolution> resolutions,List<String> settings,List<String> upgradeChecks,String limitation,PcGameRequirements requirements,String explanationStatus,List<UpgradeRecommendation> recommendations){this(appId,gameName,grade,checks,summary,resolutions,settings,upgradeChecks,limitation,requirements,explanationStatus,recommendations,List.of());}
 }
}
