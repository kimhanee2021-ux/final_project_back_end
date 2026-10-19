package org.ict.final_project_back_end.domain.aiPc.service;
import org.springframework.stereotype.Service;import org.springframework.web.multipart.MultipartFile;
import org.ict.final_project_back_end.domain.aiCommon.service.*;import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import java.util.*;import static org.springframework.http.HttpStatus.*;

@Service
public class PcDiagnosisService {
 private final GeminiPcClient gemini;private final AiRateLimit limit;private final UserRepository users;private final PcRequirementsClient requirements;
 public PcDiagnosisService(GeminiPcClient g,AiRateLimit l,UserRepository u,PcRequirementsClient r){gemini=g;limit=l;users=u;requirements=r;}
 private void authenticated(Long id){if(id==null||!users.existsById(id))throw new AiException(UNAUTHORIZED,"로그인 후 PC 진단을 이용해주세요.");}
 public java.util.List<PcRequirementsClient.SearchGame> search(Long id,String keyword){authenticated(id);return requirements.search(keyword);}
 public PcDiagnosis.Extraction extract(Long id,MultipartFile image){
  authenticated(id);if(image.isEmpty())throw new AiException(BAD_REQUEST,"사진을 선택해주세요.");if(image.getSize()>10485760)throw new AiException(PAYLOAD_TOO_LARGE,"10MB 이하 사진을 선택해주세요.");
  String mime=image.getContentType();if(mime==null||!Set.of("image/png","image/jpeg","image/webp").contains(mime))throw new AiException(BAD_REQUEST,"JPG·PNG·WEBP 사진을 선택해주세요.");
  byte[] bytes;try{bytes=image.getBytes();AiImageValidator.validateImage(bytes,mime);}catch(AiException e){throw e;}catch(Exception e){throw new AiException(BAD_REQUEST,"정상적인 사진을 선택해주세요.");}
  limit.begin(id);try{return gemini.extract(bytes,mime);}finally{limit.end(id);}
 }
 public PcDiagnosis.Assessment assess(Long id,PcDiagnosis.Request request){
  authenticated(id);var pc=request.specs();
  if(pc.storageTotalGB()!=null&&pc.storageFreeGB()!=null&&pc.storageFreeGB()>pc.storageTotalGB())throw new AiException(BAD_REQUEST,"남은 공간은 총 저장공간보다 클 수 없어요.");
  if(pc.ramGB()==null&& (pc.cpuModel()==null||pc.cpuModel().isBlank())&&(pc.gpuModel()==null||pc.gpuModel().isBlank()))throw new AiException(BAD_REQUEST,"먼저 PC 사양을 확인해주세요.");
  limit.begin(id);try{
   var source=requirements.get(request.appId());if(!source.windows())throw new AiException(BAD_REQUEST,"Windows PC 지원이 확인되지 않은 게임이에요.");
   var comparison=PcRules.compare(pc,source.minimum());
   try{
    var answer=gemini.explain(pc,source,comparison);
    return new PcDiagnosis.Assessment(source.appId(),source.name(),comparison.grade(),comparison.checks(),gemini.explanation(answer,"summary",1000),List.of(new PcDiagnosis.Resolution("FHD",gemini.explanation(answer,"fhd",600)),new PcDiagnosis.Resolution("QHD",gemini.explanation(answer,"qhd",600)),new PcDiagnosis.Resolution("4K",gemini.explanation(answer,"uhd",600))),gemini.list(answer,"settings"),gemini.list(answer,"upgradeChecks"),gemini.explanation(answer,"limitation",800),source,"READY");
   }catch(AiException failure){
    if(!Set.of(BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT,TOO_MANY_REQUESTS).contains(failure.getStatus()))throw failure;
    // 공식 근거와 코드 비교는 이미 확보했다. 외부 해설 실패를 성공한 AI 답변처럼 꾸미지 않는다.
    String summary="AI 해설을 가져오지 못해 공식 요구 사양 비교 결과만 표시해요. 아래 확인 항목에서 충족 여부와 비교 근거가 부족한 부분을 확인할 수 있어요.";
    String resolution="이 해상도의 실측 FPS 자료가 없어 성능을 확정하지 않아요. AI 해설은 잠시 후 다시 비교해 주세요.";
    return new PcDiagnosis.Assessment(source.appId(),source.name(),comparison.grade(),comparison.checks(),summary,List.of(new PcDiagnosis.Resolution("FHD",resolution),new PcDiagnosis.Resolution("QHD",resolution),new PcDiagnosis.Resolution("4K",resolution)),List.of(),List.of("현재 자료만으로 교체 필요성이나 부품 호환성을 확정할 수 없어요. 부품 규격·전원·장착 공간을 확인해 주세요."),"AI 생성 설명이 아닌 공식 요구 사양과 입력 사양의 코드 비교입니다. X는 실행 불가가 아니라 평가 불가이며, 실측 성능을 보장하지 않아요.",source,"UNAVAILABLE");
   }
  }finally{limit.end(id);}
 }
}
