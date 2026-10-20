package org.ict.final_project_back_end.domain.aiPc.service;
import org.springframework.stereotype.Service;import org.springframework.web.multipart.MultipartFile;
import org.ict.final_project_back_end.domain.aiCommon.service.*;import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import java.util.*;import static org.springframework.http.HttpStatus.*;

@Service
public class PcDiagnosisService {
 private final PcPartsService parts;private final GeminiPcClient gemini;private final AiRateLimit limit;private final UserRepository users;private final PcRequirementsClient requirements;
 public PcDiagnosisService(GeminiPcClient g,AiRateLimit l,UserRepository u,PcRequirementsClient r,PcPartsService p){parts=p;gemini=g;limit=l;users=u;requirements=r;}
 private void authenticated(Long id){if(id==null||!users.existsById(id))throw new AiException(UNAUTHORIZED,"로그인 후 PC 진단을 이용해주세요.");}
 public String provider(Long id){authenticated(id);return gemini.provider();}
 public java.util.List<PcRequirementsClient.SearchGame> search(Long id,String keyword){authenticated(id);return requirements.search(keyword);}
 public PcDiagnosis.Extraction extract(Long id,MultipartFile image){
  return extract(id,image,"");
 }
 public PcDiagnosis.Extraction extract(Long id,MultipartFile image,String expectedProvider){
  authenticated(id);if(!expectedProvider.isBlank()&&!expectedProvider.equals(gemini.provider()))throw new AiException(CONFLICT,"AI 공급자가 변경됐어요. 창을 다시 열어 전송 대상을 확인해주세요.");
  authenticated(id);if(image.isEmpty())throw new AiException(BAD_REQUEST,"사진을 선택해주세요.");if(image.getSize()>10485760)throw new AiException(PAYLOAD_TOO_LARGE,"10MB 이하 사진을 선택해주세요.");
  String mime=image.getContentType();if(mime==null||!Set.of("image/png","image/jpeg","image/webp").contains(mime))throw new AiException(BAD_REQUEST,"JPG·PNG·WEBP 사진을 선택해주세요.");
  byte[] bytes;try{bytes=image.getBytes();AiImageValidator.validateImage(bytes,mime);}catch(AiException e){throw e;}catch(Exception e){throw new AiException(BAD_REQUEST,"정상적인 사진을 선택해주세요.");}
  long started=System.nanoTime();limit.begin(id);try{return gemini.extract(bytes,mime);}finally{org.slf4j.LoggerFactory.getLogger(getClass()).info("PC timing phase=photo totalMs={}",(System.nanoTime()-started)/1000000);limit.end(id);}
 }
 // 최대 3개 공식 조회만 병렬 처리한다. 입력 순서를 유지하고 임의 재시도하지 않는다.
 private List<PcGameRequirements> loadRequirements(List<Long> ids){
  try(var executor=java.util.concurrent.Executors.newFixedThreadPool(Math.min(3,ids.size()))){
   var futures=ids.stream().map(appId->executor.submit(()->requirements.get(appId))).toList();
   var result=new ArrayList<PcGameRequirements>();
   try{for(var future:futures)result.add(future.get());return List.copyOf(result);}
   catch(InterruptedException e){futures.forEach(f->f.cancel(true));Thread.currentThread().interrupt();throw new AiException(SERVICE_UNAVAILABLE,"공식 사양 조회가 중단됐어요.");}
   catch(java.util.concurrent.ExecutionException e){futures.forEach(f->f.cancel(true));if(e.getCause() instanceof AiException ai)throw ai;throw new AiException(SERVICE_UNAVAILABLE,"공식 사양을 확인하지 못했어요.");}
  }
 }
 public PcDiagnosis.Overview overview(Long id,PcDiagnosis.OverviewRequest request){
  authenticated(id);
  if(new HashSet<>(request.appIds()).size()!=request.appIds().size())throw new AiException(BAD_REQUEST,"중복 게임을 제외해주세요.");
  limit.begin(id);try{
   var games=loadRequirements(request.appIds());
   var offers=PcPartCandidates.includeUnpriced(parts.cachedOffers(id));
   var answer=gemini.overview(request.specs(),games,offers);
   String grade=answer.path("overallGrade").asText();
   if(!Set.of("A","B","C","D","E","UNKNOWN").contains(grade))throw new AiException(BAD_GATEWAY,"종합 등급 형식이 올바르지 않아요.");
   if(!"IDENTIFIED_SINGLE".equals(PcHardwarePolicy.gpuIdentity(request.specs()))||request.specs().cpuModel()==null||request.specs().cpuModel().isBlank()||request.specs().ramGB()==null)grade="UNKNOWN";
   var explanation=gemini.list(answer,"overallExplanation");
   if(explanation.size()!=4)throw new AiException(BAD_GATEWAY,"종합 설명을 확인하지 못했어요.");
   return new PcDiagnosis.Overview(grade,games.size(),explanation,PcHardwarePolicy.advice(request.specs(),PcComponentAdviceReader.parse(answer.path("componentAdvice"),offers)),gemini.actualProvider());
  }finally{limit.end(id);}
 }
 public List<PcDiagnosis.Assessment> assessBatch(Long id,PcDiagnosis.BatchRequest request){
  authenticated(id);var pc=request.specs();
  if(pc.storageTotalGB()!=null&&pc.storageFreeGB()!=null&&pc.storageFreeGB()>pc.storageTotalGB())throw new AiException(BAD_REQUEST,"남은 공간은 총 저장공간보다 클 수 없어요.");
  if(pc.ramGB()==null&&(pc.cpuModel()==null||pc.cpuModel().isBlank())&&(pc.gpuModel()==null||pc.gpuModel().isBlank()))throw new AiException(BAD_REQUEST,"먼저 PC 사양을 확인해주세요.");
  if(new HashSet<>(request.appIds()).size()!=request.appIds().size())throw new AiException(BAD_REQUEST,"중복 게임을 제외해주세요.");
  long started=System.nanoTime(),phaseStarted=started;limit.begin(id);String stage="REQUIREMENTS";long currentAppId=0;try{
   var games=loadRequirements(request.appIds());
   if(games.stream().anyMatch(g->!g.windows()))throw new AiException(BAD_REQUEST,"Windows 지원이 확인되지 않은 게임이 있어요.");
   org.slf4j.LoggerFactory.getLogger(getClass()).info("PC timing phase=requirements games={} ms={}",games.size(),(System.nanoTime()-phaseStarted)/1000000);phaseStarted=System.nanoTime();
   var comparisons=games.stream().map(g->PcRules.compare(pc,g.minimum())).toList();
   stage="PART_OFFERS";var offers=PcPartCandidates.includeUnpriced(parts.cachedOffers(id));stage="AI_RESPONSE";org.slf4j.LoggerFactory.getLogger(getClass()).info("PC timing phase=cachedPrices ms={}",(System.nanoTime()-phaseStarted)/1000000);phaseStarted=System.nanoTime();var answer=gemini.explainBatch(pc,games,comparisons,offers);
   org.slf4j.LoggerFactory.getLogger(getClass()).info("PC timing phase=ai ms={}",(System.nanoTime()-phaseStarted)/1000000);stage="COMPONENT_ADVICE";var advice=PcHardwarePolicy.advice(pc,PcComponentAdviceReader.parse(answer.path("componentAdvice"),offers));
   stage="RESULT_COUNT";var answers=answer.path("results");if(!answers.isArray()||answers.size()!=games.size())throw new AiException(BAD_GATEWAY,"게임별 AI 응답을 확인하지 못했어요.");
   var results=new ArrayList<PcDiagnosis.Assessment>();
   for(int i=0;i<games.size();i++){
    var g=games.get(i);currentAppId=g.appId();stage="GAME_ID";var comparison=comparisons.get(i);var entry=answers.get(i);if(!entry.path("appId").canConvertToLong()||entry.path("appId").asLong()!=g.appId())throw new AiException(BAD_GATEWAY,"게임별 AI 응답이 일치하지 않아요.");var data=entry.has("analysis")?entry.path("analysis"):entry;
    stage="GRADE";var grade=PcAiAssessment.grade(data,comparison);stage="RECOMMENDATIONS";var recommendations=PcHardwarePolicy.recommendations(pc,PcAiRecommendations.parse(data,offers));stage="TEXT_AND_TARGETS";
    results.add(new PcDiagnosis.Assessment(g.appId(),g.name(),grade,comparison.checks(),gemini.explanation(data,"summary",1000),List.of(new PcDiagnosis.Resolution("FHD",gemini.explanation(data,"fhd",600),PcFpsEvidence.find(pc,g.appId(),"FHD"),PcPerformanceTargets.parse(data,"fhd",pc,g)),new PcDiagnosis.Resolution("QHD",gemini.explanation(data,"qhd",600),PcFpsEvidence.find(pc,g.appId(),"QHD"),PcPerformanceTargets.parse(data,"qhd",pc,g)),new PcDiagnosis.Resolution("4K",gemini.explanation(data,"uhd",600),PcFpsEvidence.find(pc,g.appId(),"4K"),PcPerformanceTargets.parse(data,"uhd",pc,g))),gemini.list(data,"settings"),gemini.list(data,"upgradeChecks"),gemini.explanation(data,"limitation",800),g,"READY",recommendations,advice,gemini.actualProvider(),answer.has("overallExplanation")?gemini.list(answer,"overallExplanation"):List.of()));
   }return List.copyOf(results);
  }catch(AiException failure){org.slf4j.LoggerFactory.getLogger(PcDiagnosisService.class).warn("PC batch failed: stage={}, appId={}, status={}, provider={}",stage,currentAppId,failure.getStatus().value(),gemini.actualProvider());throw failure;}finally{org.slf4j.LoggerFactory.getLogger(getClass()).info("PC timing phase=batch totalMs={}",(System.nanoTime()-started)/1000000);limit.end(id);}
 }
 public PcDiagnosis.Assessment assess(Long id,PcDiagnosis.Request request){
  authenticated(id);var pc=request.specs();
  if(pc.storageTotalGB()!=null&&pc.storageFreeGB()!=null&&pc.storageFreeGB()>pc.storageTotalGB())throw new AiException(BAD_REQUEST,"남은 공간은 총 저장공간보다 클 수 없어요.");
  if(pc.ramGB()==null&& (pc.cpuModel()==null||pc.cpuModel().isBlank())&&(pc.gpuModel()==null||pc.gpuModel().isBlank()))throw new AiException(BAD_REQUEST,"먼저 PC 사양을 확인해주세요.");
  limit.begin(id);try{
   var source=requirements.get(request.appId());if(!source.windows())throw new AiException(BAD_REQUEST,"Windows PC 지원이 확인되지 않은 게임이에요.");
   var comparison=PcRules.compare(pc,source.minimum());
   String stage="PART_OFFERS";
   try{
    var offers=PcPartCandidates.includeUnpriced(parts.cachedOffers(id));if(offers==null)offers=List.of();stage="AI_EXPLANATION";var answer=gemini.explain(pc,source,comparison,offers);stage="RECOMMENDATION_VALIDATION";var recommendations=PcHardwarePolicy.recommendations(pc,PcAiRecommendations.parse(answer,offers));stage="GRADE_VALIDATION";var aiGrade=PcAiAssessment.grade(answer,comparison);stage="TEXT_VALIDATION";
    return new PcDiagnosis.Assessment(source.appId(),source.name(),aiGrade,comparison.checks(),gemini.explanation(answer,"summary",1000),List.of(new PcDiagnosis.Resolution("FHD",gemini.explanation(answer,"fhd",600),PcFpsEvidence.find(pc,source.appId(),"FHD"),PcPerformanceTargets.parse(answer,"fhd",pc,source)),new PcDiagnosis.Resolution("QHD",gemini.explanation(answer,"qhd",600),PcFpsEvidence.find(pc,source.appId(),"QHD"),PcPerformanceTargets.parse(answer,"qhd",pc,source)),new PcDiagnosis.Resolution("4K",gemini.explanation(answer,"uhd",600),PcFpsEvidence.find(pc,source.appId(),"4K"),PcPerformanceTargets.parse(answer,"uhd",pc,source))),gemini.list(answer,"settings"),gemini.list(answer,"upgradeChecks"),gemini.explanation(answer,"limitation",800),source,"READY",recommendations,PcHardwarePolicy.advice(pc,PcComponentAdviceReader.parse(answer.path("componentAdvice"),offers)),gemini.actualProvider(),answer.has("overallExplanation")?gemini.list(answer,"overallExplanation"):List.of());
   }catch(AiException failure){
    org.slf4j.LoggerFactory.getLogger(PcDiagnosisService.class).warn("PC assessment failure: stage={}, status={}",stage,failure.getStatus().value());
    if(!Set.of(BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT,TOO_MANY_REQUESTS).contains(failure.getStatus()))throw failure;
    // 공식 근거와 코드 비교는 이미 확보했다. 외부 해설 실패를 성공한 AI 답변처럼 꾸미지 않는다.
    boolean rateLimited=failure.getStatus()==TOO_MANY_REQUESTS;
    String summary=rateLimited?"AI 서비스 이용 한도에 도달해 추가 AI 요청을 중단했어요. 공식 요구 사양 비교 결과는 아래에서 확인할 수 있어요. 한도가 회복된 뒤 다시 비교해 주세요.":"AI 해설을 가져오지 못해 공식 요구 사양 비교 결과만 표시해요. 아래 확인 항목에서 충족 여부와 비교 근거가 부족한 부분을 확인할 수 있어요.";
    String resolution="현재 PC에서 이 해상도로 측정한 FPS가 없어 성능을 확정하지 않아요. "+(rateLimited?"AI 해설은 이용 한도가 회복된 뒤 확인할 수 있어요.":"AI 해설은 잠시 후 다시 비교해 주세요.");
    return new PcDiagnosis.Assessment(source.appId(),source.name(),comparison.grade(),comparison.checks(),summary,List.of(new PcDiagnosis.Resolution("FHD",resolution,PcFpsEvidence.find(pc,source.appId(),"FHD")),new PcDiagnosis.Resolution("QHD",resolution,PcFpsEvidence.find(pc,source.appId(),"QHD")),new PcDiagnosis.Resolution("4K",resolution,PcFpsEvidence.find(pc,source.appId(),"4K"))),List.of(),List.of("현재 자료만으로 교체 필요성이나 부품 호환성을 확정할 수 없어요. 부품 규격·전원·장착 공간을 확인해 주세요."),"AI 생성 설명이 아닌 공식 요구 사양과 입력 사양의 코드 비교입니다. X는 실행 불가가 아니라 평가 불가이며, 실측 성능을 보장하지 않아요.",source,rateLimited?"RATE_LIMITED":"UNAVAILABLE",List.of());
   }
  }finally{limit.end(id);}
 }
}
