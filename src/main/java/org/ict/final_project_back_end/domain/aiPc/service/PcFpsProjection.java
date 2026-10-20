package org.ict.final_project_back_end.domain.aiPc.service;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import java.util.*;

/** 동일 CPU/GPU 평균 실측값을 현재 PC의 참고치로 전이한다. 정확도 검증된 예측 모델은 아니다. */
public final class PcFpsProjection {
 private PcFpsProjection(){}
 public static PcFps.Projection project(PcSpecs pc,List<PcFps.Sample> samples){
  if(pc.ramGB()==null)return null;
  var eligible=samples.stream().filter(s->!Boolean.FALSE.equals(s.projectionEligible())).filter(s->key(s.cpu()).equals(key(pc.cpuModel()))&&key(s.gpu()).equals(key(pc.gpuModel()))&&s.ramGB()!=null&&pc.ramGB()>=s.ramGB()&&s.averageFps()>0&&Double.isFinite(s.averageFps())).sorted(Comparator.comparingInt(s->s.preset().toLowerCase(Locale.ROOT).contains(" high ")?0:1)).toList();
  if(eligible.isEmpty())return null;
  var anchor=eligible.getFirst();
  // 같은 옵션만 묶고 동일 측정 PC의 중복 게시값은 한 번만 사용한다.
  var rigs=new LinkedHashMap<String,Double>();
  for(var sample:eligible)if(sample.preset().equals(anchor.preset())&&sample.appId()==anchor.appId()&&sample.resolution().equals(anchor.resolution()))rigs.putIfAbsent(sample.testRig(),sample.averageFps());
  var sorted=rigs.values().stream().sorted().toList();int n=sorted.size();
  double median=n%2==1?sorted.get(n/2):(sorted.get(n/2-1)+sorted.get(n/2))/2;
  return new PcFps.Projection(Math.round(median),anchor.preset(),n,"SAME_CPU_GPU_REFERENCE_V1");
 }
 private static String key(String text){return Objects.toString(text,"").toLowerCase(Locale.ROOT).replace("(r)","").replace("(tm)","").replaceAll("[^\\p{L}\\p{N}]","");}
}
