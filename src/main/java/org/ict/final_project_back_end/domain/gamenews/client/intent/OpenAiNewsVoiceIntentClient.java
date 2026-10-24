package org.ict.final_project_back_end.domain.gamenews.client.intent;

import com.fasterxml.jackson.databind.JsonNode;
import org.ict.final_project_back_end.domain.game.service.GameCatalogNameForms;
import org.ict.final_project_back_end.domain.game.service.GameNameNormalizer;
import org.ict.final_project_back_end.domain.gamenews.client.OpenAiResponsesTransport;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Slots;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Turn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 한 번의 모델 호출로 의도와 검색어를 해석하며 실제 게임 식별자는 DB에서 확인합니다.
@Component
public class OpenAiNewsVoiceIntentClient implements NewsVoiceIntentAiClient {
  private final OpenAiResponsesTransport transport;

  public OpenAiNewsVoiceIntentClient(OpenAiResponsesTransport transport) {
    this.transport = transport;
  }

  @Override
  public Slots extract(String text) {
    return extract(text, new AiContext(null, null, null, false, null, List.of()));
  }

  @Override
  public Slots extract(String text, AiContext context) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("gameQuery", Map.of("type", List.of("string", "null")));
    properties.put(
        "category",
        Map.of(
            "type",
            List.of("string", "null"),
            "enum",
            Arrays.asList(null, "NOTICE", "PATCH", "EVENT", "MAINTENANCE", "DEV_NEWS", "OTHER")));
    for (String field : List.of("allCategories", "multipleCategories", "useCurrentGame"))
      properties.put(field, Map.of("type", "boolean"));
    properties.put(
        "command",
        Map.of(
            "type",
            "string",
            "enum",
            List.of("SEARCH", "REPLAY", "RESET", "CHANGE_GAME", "STOP", "UNSUPPORTED")));
    properties.put("gameAmbiguous", Map.of("type", "boolean"));
    properties.put(
        "gameQueries", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 4));
    var reply =
        transport.generate(
            NewsVoiceIntentContract.PROMPT.replace(
                    "정확히 다음 여섯 필드만 가진 JSON 객체를 출력하세요:", "기본 의도 필드는 다음과 같습니다:")
                + """
        추가 필드 gameQueries를 반드시 반환하세요. gameQuery의 영어 정식명, 한국어 통용명, 약칭을 검색어 최대 4개로 확장합니다.
        원문 gameQuery는 발화 또는 허용된 최근 대화에 있는 이름을 그대로 보존하고, 번역은 gameQueries에만 넣으세요.
        숫자/버전/부제는 보존하세요. 약칭이 여러 작품이면 여러 검색어를 반환하고 임의로 단정하지 마세요.
        모르는 이름은 원문을 보존하세요. gameQuery=null 또는 SEARCH 외 명령이면 gameQueries=[]입니다.
        gameAmbiguous도 반드시 반환하세요. 발음 유사성/오인식으로 추정했거나 어떤 작품인지 불분명하면 true입니다.
        정식명/확실한 번역 또는 통용명이면 false입니다. 후보가 하나라는 이유로 발음이 다른 이름을 단정하지 마세요.
        앞의 여섯 필드에 gameQueries와 gameAmbiguous를 더한 정확히 여덟 필드의 JSON만 반환하세요.
        """,
            Map.of("transcript", text, "currentContext", context),
            Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                properties,
                "required",
                List.copyOf(properties.keySet())));
    return parse(reply, text, context);
  }

  Slots parse(JsonNode reply, String text, AiContext context) {
    if (reply == null
        || !reply.isObject()
        || reply.size() != 8
        || !reply.path("gameAmbiguous").isBoolean()
        || !reply.path("gameQueries").isArray()
        || reply.path("gameQueries").size() > 4) throw NewsVoiceIntentContract.unavailable();
    // 핵심 의도 JSON을 별도의 응답 형식으로 감싸지 않고 직접 검증합니다.
    var core = reply.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) core)
        .remove(List.of("gameQueries", "gameAmbiguous"));
    Slots slots = NewsVoiceIntentContract.parse(core);
    String provenance =
        text
            + " "
            + context.history().stream().map(Turn::transcript).reduce("", (a, b) -> a + " " + b);
    if (slots.gameQuery() != null
        && !GameNameNormalizer.normalize(provenance)
            .contains(GameNameNormalizer.normalize(slots.gameQuery())))
      throw NewsVoiceIntentContract.unavailable();
    List<String> queries = new ArrayList<>();
    for (var value : reply.path("gameQueries")) {
      if (!value.isTextual()) throw NewsVoiceIntentContract.unavailable();
      String query = GameNameNormalizer.query(value.asText());
      if (slots.gameQuery() == null
          || (slots.gameQuery().matches(".*[0-9].*")
              && !GameCatalogNameForms.sameNumbers(slots.gameQuery(), query)))
        throw NewsVoiceIntentContract.unavailable();
      if (!queries.contains(query)) queries.add(query);
    }
    return new Slots(
        slots.gameQuery(),
        slots.category(),
        slots.allCategories(),
        slots.multipleCategories(),
        slots.useCurrentGame(),
        slots.command(),
        queries,
        reply.path("gameAmbiguous").asBoolean());
  }
}
