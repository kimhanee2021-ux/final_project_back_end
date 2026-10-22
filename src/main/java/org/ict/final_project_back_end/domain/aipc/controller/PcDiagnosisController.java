package org.ict.final_project_back_end.domain.aipc.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.ict.final_project_back_end.domain.aipc.client.PcRequirementsClient;
import org.ict.final_project_back_end.domain.aipc.dto.PcDiagnosis;
import org.ict.final_project_back_end.domain.aipc.dto.PcPart;
import org.ict.final_project_back_end.domain.aipc.service.PcDiagnosisService;
import org.ict.final_project_back_end.domain.aipc.service.PcPartsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/pc")
public class PcDiagnosisController {
  private final PcDiagnosisService service;
  private final PcPartsService parts;

  public PcDiagnosisController(
      PcDiagnosisService s, PcPartsService p) {
    service = s;
    parts = p;
  }

  @GetMapping("/provider")
  public Map<String, String> provider(@AuthenticationPrincipal Long id) {
    return Map.of("provider", service.provider(id), "contractVersion", "pc-eight-v3");
  }

  @GetMapping("/parts")
  public List<PcPart.Offer> parts(
      @AuthenticationPrincipal Long id) {
    return parts.offers(id);
  }

  @GetMapping("/games")
  public List<PcRequirementsClient.SearchGame> games(@AuthenticationPrincipal Long id, @RequestParam("keyword") String keyword) {
    return service.search(id, keyword);
  }

  @PostMapping(value = "/extract", consumes = "multipart/form-data")
  public PcDiagnosis.Extraction extract(
      @AuthenticationPrincipal Long id,
      @RequestParam("image") MultipartFile image,
      @RequestParam(value = "provider", defaultValue = "") String provider) {
    return service.extract(id, image, provider);
  }

  @PostMapping("/assess-batch")
  public List<PcDiagnosis.Assessment> batch(
      @AuthenticationPrincipal Long id, @Valid @RequestBody PcDiagnosis.BatchRequest request) {
    return service.assessBatch(id, request);
  }

  @PostMapping("/overview")
  public PcDiagnosis.Overview overview(
      @AuthenticationPrincipal Long id, @Valid @RequestBody PcDiagnosis.OverviewRequest request) {
    return service.overview(id, request);
  }

  @PostMapping("/assess")
  public PcDiagnosis.Assessment assess(
      @AuthenticationPrincipal Long id, @Valid @RequestBody PcDiagnosis.Request request) {
    return service.assess(id, request);
  }
}
