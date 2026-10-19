package org.ict.final_project_back_end.domain.aiPc.dto;
import java.util.List;
/** 외부 PC의 측정값과 현재 PC의 예상 범위를 구분한다. 사용자 PC 실측 결과가 아니다. */
public final class PcFps {
 private PcFps(){}
 public record Sample(String id,long appId,String gpu,String cpu,Double ramGB,String ramDetails,String resolution,String preset,double averageFps,String gameBuild,String renderingMode,String measuredAt,String sourceUrl,String testRig){}
 public record Projection(double averageFps,String preset,int testSystems,String method){}
 public record Evidence(String status,Double lowerFps,Double upperFps,String preset,String confidence,List<Sample> samples,List<String> notes,Projection projection){
  public Evidence(String status,Double lower,Double upper,String preset,String confidence,List<Sample> samples,List<String> notes){this(status,lower,upper,preset,confidence,samples,notes,null);}
 }
}
