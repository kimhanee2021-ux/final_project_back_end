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
   var comparison=PcRules.compare(pc,source.minimum());var answer=gemini.explain(pc,source,comparison);
   return new PcDiagnosis.Assessment(source.appId(),source.name(),comparison.grade(),comparison.checks(),gemini.explanation(answer,"summary",1000),List.of(new PcDiagnosis.Resolution("FHD",gemini.explanation(answer,"fhd",600)),new PcDiagnosis.Resolution("QHD",gemini.explanation(answer,"qhd",600)),new PcDiagnosis.Resolution("4K",gemini.explanation(answer,"uhd",600))),gemini.list(answer,"settings"),gemini.list(answer,"upgradeChecks"),gemini.explanation(answer,"limitation",800),source);
  }finally{limit.end(id);}
 }
}
