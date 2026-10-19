package org.ict.final_project_back_end.domain.aiPc.controller;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.multipart.MultipartFile;import jakarta.validation.Valid;
import org.ict.final_project_back_end.domain.aiPc.service.PcDiagnosisService;import org.ict.final_project_back_end.domain.aiPc.dto.PcDiagnosis;
@RestController @RequestMapping("/api/ai/pc")
public class PcDiagnosisController {
 private final PcDiagnosisService service;private final org.ict.final_project_back_end.domain.aiPc.service.PcPartsService parts;
 public PcDiagnosisController(PcDiagnosisService s,org.ict.final_project_back_end.domain.aiPc.service.PcPartsService p){service=s;parts=p;}
 @GetMapping("/provider") public java.util.Map<String,String> provider(@AuthenticationPrincipal Long id){return java.util.Map.of("provider",service.provider(id));}
 @GetMapping("/parts") public java.util.List<org.ict.final_project_back_end.domain.aiPc.dto.PcPart.Offer> parts(@AuthenticationPrincipal Long id){return parts.offers(id);}
 @GetMapping("/games") public java.util.List<org.ict.final_project_back_end.domain.aiPc.service.PcRequirementsClient.SearchGame> games(@AuthenticationPrincipal Long id,@RequestParam("keyword")String keyword){return service.search(id,keyword);}
 @PostMapping(value="/extract",consumes="multipart/form-data") public PcDiagnosis.Extraction extract(@AuthenticationPrincipal Long id,@RequestParam("image")MultipartFile image,@RequestParam(value="provider",defaultValue="")String provider){return service.extract(id,image,provider);}
 @PostMapping("/assess") public PcDiagnosis.Assessment assess(@AuthenticationPrincipal Long id,@Valid @RequestBody PcDiagnosis.Request request){return service.assess(id,request);}
}
