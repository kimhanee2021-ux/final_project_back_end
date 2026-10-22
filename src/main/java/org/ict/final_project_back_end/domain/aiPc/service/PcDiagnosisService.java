package org.ict.final_project_back_end.domain.aiPc.service;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiCommon.service.*;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PcDiagnosisService {
  private static final Logger log = LoggerFactory.getLogger(PcDiagnosisService.class);
  private static final long MAX_IMAGE_BYTES = 10485760;
  private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
  private static final Set<String> GRADES = Set.of("A", "B", "C", "D", "E", "UNKNOWN");

  private final PcPartsService parts;
  private final GeminiPcClient gemini;
  private final AiRateLimit limit;
  private final UserRepository users;
  private final PcRequirementsClient requirements;

  public PcDiagnosisService(
      GeminiPcClient g, AiRateLimit l, UserRepository u, PcRequirementsClient r, PcPartsService p) {
    parts = p;
    gemini = g;
    limit = l;
    users = u;
    requirements = r;
  }

  private void authenticated(Long id) {
    if (id == null || !users.existsById(id))
      throw new AiException(UNAUTHORIZED, "로그인 후 PC 진단을 이용해주세요.");
  }

  public String provider(Long id) {
    authenticated(id);
    return gemini.provider();
  }

  public List<PcRequirementsClient.SearchGame> search(Long id, String keyword) {
    authenticated(id);
    return requirements.search(keyword);
  }

  public PcDiagnosis.Extraction extract(Long id, MultipartFile image, String expectedProvider) {
    authenticated(id);
    if (!expectedProvider.isBlank() && !expectedProvider.equals(gemini.provider()))
      throw new AiException(CONFLICT, "AI 공급자가 변경됐어요. 창을 다시 열어 전송 대상을 확인해주세요.");
    byte[] bytes = readImage(image);
    String mime = image.getContentType();
    long started = System.nanoTime();
    limit.begin(id);
    try {
      return gemini.extract(bytes, mime);
    } finally {
      log.info("PC timing phase=photo totalMs={}", elapsedMs(started));
      limit.end(id);
    }
  }

  private byte[] readImage(MultipartFile image) {
    if (image.isEmpty()) throw new AiException(BAD_REQUEST, "사진을 선택해주세요.");
    if (image.getSize() > MAX_IMAGE_BYTES)
      throw new AiException(PAYLOAD_TOO_LARGE, "10MB 이하 사진을 선택해주세요.");
    String mime = image.getContentType();
    if (mime == null || !IMAGE_TYPES.contains(mime))
      throw new AiException(BAD_REQUEST, "JPG·PNG·WEBP 사진을 선택해주세요.");
    try {
      byte[] bytes = image.getBytes();
      AiImageValidator.validateImage(bytes, mime);
      return bytes;
    } catch (AiException e) {
      throw e;
    } catch (Exception e) {
      throw new AiException(BAD_REQUEST, "정상적인 사진을 선택해주세요.");
    }
  }

  // 최대 3개 공식 조회만 병렬 처리한다. 입력 순서를 유지하고 임의 재시도하지 않는다.
  private List<PcGameRequirements> loadRequirements(List<Long> ids) {
    try (var executor = Executors.newFixedThreadPool(Math.min(3, ids.size()))) {
      var futures =
          ids.stream().map(appId -> executor.submit(() -> requirements.get(appId))).toList();
      var result = new ArrayList<PcGameRequirements>();
      try {
        for (var future : futures) result.add(future.get());
        return List.copyOf(result);
      } catch (InterruptedException e) {
        futures.forEach(f -> f.cancel(true));
        Thread.currentThread().interrupt();
        throw new AiException(SERVICE_UNAVAILABLE, "공식 사양 조회가 중단됐어요.");
      } catch (ExecutionException e) {
        futures.forEach(f -> f.cancel(true));
        if (e.getCause() instanceof AiException ai) throw ai;
        throw new AiException(SERVICE_UNAVAILABLE, "공식 사양을 확인하지 못했어요.");
      }
    }
  }

  public PcDiagnosis.Overview overview(Long id, PcDiagnosis.OverviewRequest request) {
    authenticated(id);
    requireDistinct(request.appIds());
    limit.begin(id);
    try {
      var games = loadRequirements(request.appIds());
      var offers = PcPartCandidates.includeUnpriced(parts.cachedOffers(id));
      var answer = gemini.overview(request.specs(), games, offers);
      String grade = answer.path("overallGrade").asText();
      if (!GRADES.contains(grade)) throw new AiException(BAD_GATEWAY, "종합 등급 형식이 올바르지 않아요.");
      if (!"IDENTIFIED_SINGLE".equals(PcHardwarePolicy.gpuIdentity(request.specs()))
          || request.specs().cpuModel() == null
          || request.specs().cpuModel().isBlank()
          || request.specs().ramGB() == null) grade = "UNKNOWN";
      var explanation = gemini.list(answer, "overallExplanation");
      if (explanation.size() != 4) throw new AiException(BAD_GATEWAY, "종합 설명을 확인하지 못했어요.");
      return new PcDiagnosis.Overview(
          grade,
          games.size(),
          explanation,
          PcHardwarePolicy.advice(
              request.specs(),
              PcComponentAdviceReader.parse(answer.path("componentAdvice"), offers)),
          gemini.actualProvider());
    } finally {
      limit.end(id);
    }
  }

  public List<PcDiagnosis.Assessment> assessBatch(Long id, PcDiagnosis.BatchRequest request) {
    authenticated(id);
    var pc = request.specs();
    validateSpecs(pc);
    requireDistinct(request.appIds());
    long started = System.nanoTime(), phaseStarted = started;
    limit.begin(id);
    String stage = "REQUIREMENTS";
    long currentAppId = 0;
    try {
      var games = loadRequirements(request.appIds());
      if (games.stream().anyMatch(g -> !g.windows()))
        throw new AiException(BAD_REQUEST, "Windows 지원이 확인되지 않은 게임이 있어요.");
      log.info("PC timing phase=requirements games={} ms={}", games.size(), elapsedMs(phaseStarted));
      phaseStarted = System.nanoTime();
      var comparisons = games.stream().map(g -> PcRules.compare(pc, g.minimum())).toList();
      stage = "PART_OFFERS";
      var offers = PcPartCandidates.includeUnpriced(parts.cachedOffers(id));
      stage = "AI_RESPONSE";
      log.info("PC timing phase=cachedPrices ms={}", elapsedMs(phaseStarted));
      phaseStarted = System.nanoTime();
      var answer = gemini.explainBatch(pc, games, comparisons, offers);
      log.info("PC timing phase=ai ms={}", elapsedMs(phaseStarted));
      stage = "COMPONENT_ADVICE";
      var advice =
          PcHardwarePolicy.advice(
              pc, PcComponentAdviceReader.parse(answer.path("componentAdvice"), offers));
      stage = "RESULT_COUNT";
      var answers = answer.path("results");
      if (!answers.isArray() || answers.size() != games.size())
        throw new AiException(BAD_GATEWAY, "게임별 AI 응답을 확인하지 못했어요.");
      var results = new ArrayList<PcDiagnosis.Assessment>();
      for (int i = 0; i < games.size(); i++) {
        var g = games.get(i);
        currentAppId = g.appId();
        stage = "GAME_ID";
        var comparison = comparisons.get(i);
        var entry = answers.get(i);
        if (!entry.path("appId").canConvertToLong() || entry.path("appId").asLong() != g.appId())
          throw new AiException(BAD_GATEWAY, "게임별 AI 응답이 일치하지 않아요.");
        var data = entry.has("analysis") ? entry.path("analysis") : entry;
        stage = "GRADE";
        var grade = PcAiAssessment.grade(data, comparison);
        stage = "RECOMMENDATIONS";
        var recommendations =
            PcHardwarePolicy.recommendations(pc, PcAiRecommendations.parse(data, offers));
        stage = "TEXT_AND_TARGETS";
        results.add(
            readyAssessment(pc, g, comparison, grade, data, answer, recommendations, advice));
      }
      return List.copyOf(results);
    } catch (AiException failure) {
      log.warn(
          "PC batch failed: stage={}, appId={}, status={}, provider={}",
          stage,
          currentAppId,
          failure.getStatus().value(),
          gemini.actualProvider());
      throw failure;
    } finally {
      log.info("PC timing phase=batch totalMs={}", elapsedMs(started));
      limit.end(id);
    }
  }

  public PcDiagnosis.Assessment assess(Long id, PcDiagnosis.Request request) {
    authenticated(id);
    var pc = request.specs();
    validateSpecs(pc);
    limit.begin(id);
    try {
      var source = requirements.get(request.appId());
      if (!source.windows()) throw new AiException(BAD_REQUEST, "Windows PC 지원이 확인되지 않은 게임이에요.");
      var comparison = PcRules.compare(pc, source.minimum());
      String stage = "PART_OFFERS";
      try {
        var offers = PcPartCandidates.includeUnpriced(parts.cachedOffers(id));
        if (offers == null) offers = List.of();
        stage = "AI_EXPLANATION";
        var answer = gemini.explain(pc, source, comparison, offers);
        stage = "RECOMMENDATION_VALIDATION";
        var recommendations =
            PcHardwarePolicy.recommendations(pc, PcAiRecommendations.parse(answer, offers));
        stage = "GRADE_VALIDATION";
        var aiGrade = PcAiAssessment.grade(answer, comparison);
        stage = "TEXT_VALIDATION";
        var advice =
            PcHardwarePolicy.advice(
                pc, PcComponentAdviceReader.parse(answer.path("componentAdvice"), offers));
        return readyAssessment(
            pc, source, comparison, aiGrade, answer, answer, recommendations, advice);
      } catch (AiException failure) {
        log.warn("PC assessment failure: stage={}, status={}", stage, failure.getStatus().value());
        if (!Set.of(BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT, TOO_MANY_REQUESTS)
            .contains(failure.getStatus())) throw failure;
        return unavailableAssessment(pc, source, comparison, failure);
      }
    } finally {
      limit.end(id);
    }
  }

  // AI 답변(data)을 검증하면서 게임 하나의 진단 결과로 만든다. 종합 설명은 overall에서 읽는다.
  private PcDiagnosis.Assessment readyAssessment(
      PcSpecs pc,
      PcGameRequirements game,
      PcRules.Comparison comparison,
      String grade,
      JsonNode data,
      JsonNode overall,
      List<PcDiagnosis.UpgradeRecommendation> recommendations,
      List<PcComponentAdvice> advice) {
    return new PcDiagnosis.Assessment(
        game.appId(),
        game.name(),
        grade,
        comparison.checks(),
        gemini.explanation(data, "summary", 1000),
        List.of(
            resolution(pc, game, data, "FHD", "fhd"),
            resolution(pc, game, data, "QHD", "qhd"),
            resolution(pc, game, data, "4K", "uhd")),
        gemini.list(data, "settings"),
        gemini.list(data, "upgradeChecks"),
        gemini.explanation(data, "limitation", 800),
        game,
        "READY",
        recommendations,
        advice,
        gemini.actualProvider(),
        overall.has("overallExplanation") ? gemini.list(overall, "overallExplanation") : List.of());
  }

  private PcDiagnosis.Resolution resolution(
      PcSpecs pc, PcGameRequirements game, JsonNode data, String name, String key) {
    return new PcDiagnosis.Resolution(
        name,
        gemini.explanation(data, key, 600),
        PcFpsEvidence.find(pc, game.appId(), name),
        PcPerformanceTargets.parse(data, key, pc, game));
  }

  // 공식 근거와 코드 비교는 이미 확보했다. 외부 해설 실패를 성공한 AI 답변처럼 꾸미지 않는다.
  private PcDiagnosis.Assessment unavailableAssessment(
      PcSpecs pc, PcGameRequirements source, PcRules.Comparison comparison, AiException failure) {
    boolean rateLimited = failure.getStatus() == TOO_MANY_REQUESTS;
    String summary =
        rateLimited
            ? "AI 서비스 이용 한도에 도달해 추가 AI 요청을 중단했어요. 공식 요구 사양 비교 결과는 아래에서 확인할 수 있어요. 한도가 회복된 뒤 다시"
                + " 비교해 주세요."
            : "AI 해설을 가져오지 못해 공식 요구 사양 비교 결과만 표시해요. 아래 확인 항목에서 충족 여부와 비교 근거가 부족한 부분을 확인할 수"
                + " 있어요.";
    String resolution =
        "현재 PC에서 이 해상도로 측정한 FPS가 없어 성능을 확정하지 않아요. "
            + (rateLimited ? "AI 해설은 이용 한도가 회복된 뒤 확인할 수 있어요." : "AI 해설은 잠시 후 다시 비교해 주세요.");
    return new PcDiagnosis.Assessment(
        source.appId(),
        source.name(),
        comparison.grade(),
        comparison.checks(),
        summary,
        List.of(
            new PcDiagnosis.Resolution(
                "FHD", resolution, PcFpsEvidence.find(pc, source.appId(), "FHD")),
            new PcDiagnosis.Resolution(
                "QHD", resolution, PcFpsEvidence.find(pc, source.appId(), "QHD")),
            new PcDiagnosis.Resolution(
                "4K", resolution, PcFpsEvidence.find(pc, source.appId(), "4K"))),
        List.of(),
        List.of("현재 자료만으로 교체 필요성이나 부품 호환성을 확정할 수 없어요. 부품 규격·전원·장착 공간을 확인해 주세요."),
        "AI 생성 설명이 아닌 공식 요구 사양과 입력 사양의 코드 비교입니다. X는 실행 불가가 아니라 평가 불가이며, 실측 성능을 보장하지 않아요.",
        source,
        rateLimited ? "RATE_LIMITED" : "UNAVAILABLE",
        List.of());
  }

  private static void validateSpecs(PcSpecs pc) {
    if (pc.storageTotalGB() != null
        && pc.storageFreeGB() != null
        && pc.storageFreeGB() > pc.storageTotalGB())
      throw new AiException(BAD_REQUEST, "남은 공간은 총 저장공간보다 클 수 없어요.");
    if (pc.ramGB() == null
        && (pc.cpuModel() == null || pc.cpuModel().isBlank())
        && (pc.gpuModel() == null || pc.gpuModel().isBlank()))
      throw new AiException(BAD_REQUEST, "먼저 PC 사양을 확인해주세요.");
  }

  private static void requireDistinct(List<Long> appIds) {
    if (new HashSet<>(appIds).size() != appIds.size())
      throw new AiException(BAD_REQUEST, "중복 게임을 제외해주세요.");
  }

  private static long elapsedMs(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1000000;
  }
}
