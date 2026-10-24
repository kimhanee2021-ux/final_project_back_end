package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenAiNewsTranscriptionClient implements NewsTranscriptionClient {
  private final String key, model, base;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();
  public OpenAiNewsTranscriptionClient(
      @Value("${app.news.stt.api-key:${OPENAI_API_KEY:}}") String key,
      @Value("${app.news.stt.model:${OPENAI_STT_MODEL:gpt-transcribe}}") String model,
      @Value("${app.news.stt.base-url:https://api.openai.com}") String base) {
    this.key = key; this.model = model; this.base = base;
  }
  @Override public String transcribe(byte[] audio, String mime, List<String> hints) {
    if (key.isBlank()) throw new GameSearchException(HttpStatus.SERVICE_UNAVAILABLE, "STT_NOT_CONFIGURED",
        "다국어 음성 인식 키가 설정되지 않았어요. 검색창을 이용해 주세요.");
    if (!Set.of("gpt-transcribe", "gpt-4o-transcribe", "gpt-4o-mini-transcribe").contains(model)) throw unavailable();
    String boundary = "gs-" + UUID.randomUUID();
    try {
      var request = HttpRequest.newBuilder(URI.create(base + "/v1/audio/transcriptions"))
          .timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + key)
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(audio, mime, hints, boundary))).build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var in = response.body()) {
        if (response.statusCode() == 429) throw new GameSearchException(HttpStatus.TOO_MANY_REQUESTS, "STT_LIMIT", "음성 인식 요청이 많아요. 잠시 후 다시 시도해 주세요.");
        if (response.statusCode() != 200) throw unavailable();
        byte[] bytes = in.readNBytes(65537);
        if (bytes.length > 65536) throw unavailable();
        var node = json.readTree(bytes).get("text");
        if (node == null || !node.isTextual()) throw unavailable();
        String text = node.asText().strip();
        if (text.isBlank()) throw GameSearchException.invalid("음성을 인식하지 못했어요. 다시 말씀해 주세요.");
        if (text.length() > 500) throw GameSearchException.invalid("한 번에 500자까지 말씀해 주세요.");
        return text;
      }
    } catch (HttpTimeoutException error) {
      throw new GameSearchException(HttpStatus.GATEWAY_TIMEOUT, "STT_TIMEOUT", "음성 인식 시간이 초과됐어요. 다시 시도해 주세요.");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt(); throw unavailable();
    } catch (IOException | IllegalArgumentException error) { throw unavailable(); }
  }
  byte[] multipart(byte[] audio, String mime, List<String> hints, String boundary) throws IOException {
    var out = new ByteArrayOutputStream();
    field(out, boundary, "model", model); field(out, boundary, "response_format", "json");
    field(out, boundary, "prompt", "게임 소식 검색 / Game news search. 한국어와 영어가 섞일 수 있습니다. "
        + "고유명사 참고 / Vocabulary: " + String.join(", ", hints));
    if (model.equals("gpt-transcribe")) {
      field(out, boundary, "languages[]", "ko"); field(out, boundary, "languages[]", "en");
      for (String hint : hints) field(out, boundary, "keywords[]", hint);
    }
    String extension = switch (mime) { case "audio/ogg" -> "ogg"; case "audio/wav" -> "wav"; case "audio/mp4" -> "mp4"; default -> "webm"; };
    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech." + extension
        + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(audio); out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }
  private void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
  }
  private GameSearchException unavailable() { return new GameSearchException(HttpStatus.BAD_GATEWAY, "STT_UNAVAILABLE", "다국어 음성을 인식하지 못했어요. 다시 시도하거나 검색창을 이용해 주세요."); }
}
