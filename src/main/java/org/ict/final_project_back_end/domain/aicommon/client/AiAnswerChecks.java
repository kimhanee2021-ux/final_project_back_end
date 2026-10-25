package org.ict.final_project_back_end.domain.aicommon.client;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;

/** AI가 돌려준 JSON 칸이 약속한 모양(글자 길이, 목록 개수)인지 확인한다. 어긋나면 502로 버린다. */
public final class AiAnswerChecks {

  private AiAnswerChecks() {
  }

  // max자 이하 글자. empty=false면 빈 글자도 거절한다
  public static String checkedString(JsonNode node, int max, boolean empty) {
    if (!node.isTextual() || node.asText().length() > max || (!empty && node.asText().isBlank()))
      throw invalid();
    return node.asText();
  }

  // 6개 이하, 각 500자 이하의 비어 있지 않은 글자 목록
  public static List<String> checkedList(JsonNode node) {
    if (!node.isArray() || node.size() > 6) throw invalid();
    List<String> result = new ArrayList<>();
    for (var item : node) result.add(checkedString(item, 500, false));
    return List.copyOf(result);
  }

  public static AiException invalid() {
    return new AiException(BAD_GATEWAY, "AI 답변을 확인하지 못했습니다. 질문을 바꾸거나 다시 시도해주세요.");
  }
}
