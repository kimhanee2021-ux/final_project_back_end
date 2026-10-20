package org.ict.final_project_back_end.domain.aiPc.service;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 단순 해상도 배율·GPU 점수 비례로 FPS를 만들지 않는다. 출처 있는 참고 실측과 조건부 저신뢰 참고 전이를 구분한다. */
public final class PcFpsEvidence {
 private PcFpsEvidence(){}
 private static final List<PcFps.Sample> SAMPLES=load();
 private static final List<PcFps.PublisherTarget> PUBLISHER_TARGETS=loadTargets();
 private static List<PcFps.Sample> load(){
  var result=new ArrayList<PcFps.Sample>();
  for(String file:List.of("verified-reference.json","review-reference.json")){
   try(var stream=PcFpsEvidence.class.getResourceAsStream("/pc-benchmarks/"+file)){
    if(stream==null)throw new IllegalStateException("Missing benchmark evidence");
    result.addAll(List.of(new ObjectMapper().readValue(stream,PcFps.Sample[].class)));
   }catch(Exception failure){throw new IllegalStateException("Invalid benchmark evidence resource",failure);}
  }
  return List.copyOf(result);
 }
 private static List<PcFps.PublisherTarget> loadTargets(){
  try(var stream=PcFpsEvidence.class.getResourceAsStream("/pc-benchmarks/publisher-targets.json")){
   if(stream==null)throw new IllegalStateException("Missing publisher targets");
   return List.of(new ObjectMapper().readValue(stream,PcFps.PublisherTarget[].class));
  }catch(Exception failure){throw new IllegalStateException("Invalid publisher targets",failure);}
 }
 public static PcFps.Evidence find(PcSpecs pc,long appId,String resolution){
  var value=select(pc,appId,resolution,SAMPLES);
  List<Integer> pixels=switch(resolution){case "FHD"->List.of(1920,1080);case "QHD"->List.of(2560,1440);case "4K"->List.of(3840,2160);default->List.of();};
  var targets=PUBLISHER_TARGETS.stream().filter(t->t.appId()!=null&&t.appId()==appId&&(t.resolution()==null||t.resolution().equals(pixels))).toList();
  return new PcFps.Evidence(value.status(),value.lowerFps(),value.upperFps(),value.preset(),value.confidence(),value.samples(),value.notes(),value.projection(),targets);
 }
 public static List<PcFps.Sample> references(PcSpecs pc,long appId){return SAMPLES.stream().filter(s->s.appId()==appId&&sameGpu(pc,s)).toList();}
 public static Map<String,PcFps.Projection> projections(PcSpecs pc,long appId){var result=new LinkedHashMap<String,PcFps.Projection>();for(String resolution:List.of("FHD","QHD","4K")){var evidence=find(pc,appId,resolution);if(evidence.projection()!=null)result.put(resolution,evidence.projection());}return Map.copyOf(result);}
 /** AI는 조건/한계만 해설하고 수치는 검증된 서버 응답 영역에서 표시한다. */
 public static Map<String,Object> explanationContext(PcSpecs pc,long appId){
  var context=new LinkedHashMap<String,Object>();
  for(String resolution:List.of("FHD","QHD","4K")){
   var evidence=find(pc,appId,resolution);
   var conditions=evidence.samples().stream().limit(3).map(sample->Map.of("averageFps",sample.averageFps(),"renderingMode",sample.renderingMode(),"cpu",sample.cpu(),"gpu",sample.gpu(),"ramDetails",sample.ramDetails(),"preset",sample.preset(),"measuredAt",sample.measuredAt(),"gameBuild",sample.gameBuild(),"sourceUrl",sample.sourceUrl())).toList();
   context.put(resolution,Map.of("status",evidence.status(),"confidence",evidence.confidence(),"conditions",conditions,"publisherTargets",evidence.publisherTargets(),"limitations",evidence.notes(),"numericDisplay","수치는 서버의 참고 FPS 영역에서 별도 표시. 설명 문장에 FPS 숫자를 쓰지 마세요."));
  }
  return Map.copyOf(context);
 }
 public static PcFps.Evidence select(PcSpecs pc,long appId,String resolution,List<PcFps.Sample> samples){
  var matching=samples.stream().filter(s->s.appId()==appId&&s.resolution().equals(resolution)&&sameGpu(pc,s)&&Double.isFinite(s.averageFps())&&s.averageFps()>0&&(s.sourceUrl().startsWith("https://www.computerbase.de/artikel/grafikkarten/nvidia-geforce-amd-radeon-benchmark-test-2025.90695/")||s.sourceUrl().startsWith("https://www.computerbase.de/artikel/grafikkarten/grafikkarten-vergleich-radeon-rx-7600-xt-rx-9060-xt-geforce-rtx-4060-rtx-5060-intel-xe-arc-a580-b580-duell.93230/")||s.sourceUrl().startsWith("https://www.computerbase.de/artikel/gaming/counter-strike-2-benchmark-test.87904/")||s.sourceUrl().startsWith("https://www.computerbase.de/artikel/gaming/pubg-battlegrounds-benchmark-test.87841/")||s.sourceUrl().startsWith("https://www.notebookcheck.net/")||s.sourceUrl().startsWith("https://www.thefpsreview.com/2022/03/07/intel-12900k-uhd-770-igpu-vs-amd-5700g-vega-8-apu-performance-benchmarks/"))).toList();
  if(matching.isEmpty())return new PcFps.Evidence("NO_DATA",null,null,"","NONE",List.of(),List.of("같은 GPU·게임·해상도의 검증된 실측 자료를 아직 확보하지 못했어요. 해상도 배율로 FPS를 추측하지 않아요."));
  var notes=new ArrayList<String>();notes.add("아래 수치는 외부 측정 PC의 참고 실측이며, 현재 PC의 실제 FPS나 검증된 예상 범위가 아니에요.");
  if(matching.stream().anyMatch(s->!key(s.cpu()).equals(key(pc.cpuModel()))))notes.add("측정 CPU가 현재 PC와 다른 자료가 있어요.");
  notes.add("외부 측정의 GPU 메모리 구성·옵션·업스케일링·RT 조건을 확인하세요. 현재 PC의 낮음·네이티브 등급과 같은 조건이라는 뜻이 아니에요.");
  notes.add("RAM 규격·채널·전력 제한·드라이버·게임 빌드가 일치하는지 확인되지 않아 예상 FPS 산정은 보류했어요.");
  notes.add("평균 FPS만 제공해요. 순간 최저 FPS·1% low 또는 프레임 생성 결과와 혼동하지 마세요.");
  var projection=PcFpsProjection.project(pc,matching);
  if(projection!=null){
   notes.set(0,"동일 CPU·GPU의 과거 평균 실측값을 반올림한 참고 예상이에요. 현재 PC에서 측정한 값은 아니에요.");
   notes.set(notes.size()-2,"현재 RAM 용량이 측정 PC 이상이라는 조건만 확인했어요. RAM 세대·속도·채널, 전력·드라이버·게임 빌드 차이를 보정하지 못한 낮은 신뢰도의 임시 추정이며 정확도 검증은 아직 없어요.");
   return new PcFps.Evidence("REFERENCE_PROJECTION",null,null,projection.preset(),"LOW",matching,List.copyOf(notes),projection);
  }
  return new PcFps.Evidence("REFERENCE_ONLY",null,null,"각 측정 옵션 확인","REFERENCE",matching,List.copyOf(notes));
 }
 private static boolean sameGpu(PcSpecs pc,PcFps.Sample sample){
  if(PcHardwarePolicy.ambiguousGpu(pc.gpuModel())||!gpuKey(sample.gpu()).equals(gpuKey(pc.gpuModel())))return false;
  var actual=java.util.regex.Pattern.compile("(?i)([0-9]+)\\s*GB").matcher(Objects.toString(pc.gpuMemory(),""));
  var measured=java.util.regex.Pattern.compile("(?i)([0-9]+)\\s*GB").matcher(sample.gpu());
  if(actual.find()&&measured.find()&&!actual.group(1).equals(measured.group(1)))return false;
  return true;
 }
 private static String gpuKey(String value){
  String normalized=Objects.toString(value,"").toLowerCase(Locale.ROOT).replaceAll("\\(\\d+\\s*gb\\)|\\b\\d+\\s*gb\\b","").replaceFirst("^(nvidia|amd)\\s+","").replaceFirst("^xe arc","intel arc");
  return key(normalized);
 }
 private static String key(String value){return Objects.toString(value,"").toLowerCase(Locale.ROOT).replace("(r)","").replace("(tm)","").replaceAll("[^\\p{L}\\p{N}]","");}
}

