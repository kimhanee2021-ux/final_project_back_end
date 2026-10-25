package org.ict.final_project_back_end.domain.aicoach.client;

import java.util.List;
import java.util.Map;

// AI 코치가 쓰는 언어 모델 공급자(Gemini, Groq 등). 프롬프트를 보내고 JSON 문자열 답만 돌려준다.
// 코치 기능(CoachReplyClient, CoachInsightClient)은 이 인터페이스만 알고, 어느 공급자인지는 CoachModelRouter가 고른다.
// 실패하면 AiCoachException(notConfigured, tooManyRequests, busy, timeout, invalidResponse)을 던진다.
public interface CoachModel {

  // role: user(사용자) / model(코치). 공급자마다 다른 이름은 구현체가 바꿔 보낸다
  record Message(String role, String text) {
  }

  // schema: 답의 JSON 형식. 공급자가 지원하면 그 형식으로 답하게 한다
  record Request(String systemPrompt, List<Message> messages, Map<String, Object> schema,
                 double temperature, int maxOutputTokens) {
  }

  // 설정의 공급자 순서(chat-providers 등)에 적는 이름
  String provider();

  // 키·모델이 설정되어 있는지. 설정이 없으면 라우터가 건너뛴다
  boolean configured();

  // 모델이 끝까지 만든 JSON 문자열. 형식 검사(필드·길이)는 부르는 쪽이 한다
  String generateJson(Request request);

  // 공급자 서버가 잠깐 혼잡한 응답(500/502/503/504)인지. 429(한도 초과)나 400번대는 혼잡이 아니다
  static boolean isServerBusy(int status) {
    return status == 500 || status == 502 || status == 503 || status == 504;
  }
}
