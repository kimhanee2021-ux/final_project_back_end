package org.ict.final_project_back_end.domain.wishsales.client;

// 구매 조언 문장을 만드는 AI 공급자 하나. 프롬프트를 보내고 답 글자만 돌려준다.
// 공급자를 늘릴 때는 이 인터페이스의 구현(@Component)만 추가하고, AI_PROVIDERS 순서에 이름을 적는다.
public interface AdviceTextProvider {

  // AI_PROVIDERS에 적는 이름 (anthropic, gemini, groq)
  String name();

  // 키가 설정되어 있는지
  boolean configured();

  // HTTP 오류는 RestClientResponseException 그대로 던진다 (AdviceSentenceClient가 다음 공급자로 넘어간다)
  String complete(String prompt);
}
