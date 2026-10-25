package org.ict.final_project_back_end.domain.aicoach.client;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 화면마다 정한 공급자 순서(예: "groq,gemini")대로 CoachModel을 시도한다.
// 앞 공급자가 잠깐 쓸 수 없는 상태(미설정·한도 초과·혼잡·시간 초과)면 다음 공급자로 넘어가고,
// 답 형식이 틀린 것처럼 다른 공급자로 바꿔도 나아지지 않는 실패는 그대로 알린다.
@Slf4j
@Component
public class CoachModelRouter {

  // 다른 공급자로 넘어가면 해결될 수 있는 실패 코드 (AiCoachException.getCode())
  private static final Set<String> FALLBACK_CODES = Set.of("AI_NOT_CONFIGURED", "AI_RATE_LIMITED", "AI_BUSY", "AI_TIMEOUT");

  private final Map<String, CoachModel> models;

  public CoachModelRouter(List<CoachModel> models) {
    this.models = models.stream().collect(Collectors.toMap(CoachModel::provider, Function.identity()));
  }

  // "groq, Gemini" 같은 설정 문자열을 공백 없는 소문자 목록으로 바꾼다
  public static List<String> parseOrder(String providers) {
    return Arrays.stream(providers == null ? new String[0] : providers.split(","))
        .map(name -> name.trim().toLowerCase())
        .filter(name -> !name.isEmpty())
        .toList();
  }

  // order: 시도할 공급자 이름 순서, feature: 로그에 남길 기능 이름(chat, insight)
  public String generateJson(List<String> order, String feature, CoachModel.Request request) {
    AiCoachException lastFailure = AiCoachException.notConfigured();
    for (String provider : order) {
      CoachModel model = models.get(provider);
      if (model == null || !model.configured()) {
        continue;
      }
      long start = System.currentTimeMillis();
      try {
        String answer = model.generateJson(request);
        log.info("AI 코치 {} 응답: provider={}, {}ms", feature, provider, System.currentTimeMillis() - start);
        return answer;
      } catch (AiCoachException e) {
        if (!FALLBACK_CODES.contains(e.getCode())) {
          throw e;
        }
        log.warn("AI 코치 {} 공급자 전환: provider={} 실패({}), {}ms", feature, provider, e.getCode(),
            System.currentTimeMillis() - start);
        lastFailure = e;
      }
    }
    throw lastFailure;
  }
}
