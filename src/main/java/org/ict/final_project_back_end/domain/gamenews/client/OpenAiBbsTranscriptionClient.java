package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** BBS 전용 OpenAI 연결. 코치 설정, Gemini, 자동 재시도와 분리한다. */
@Component
public class OpenAiBbsTranscriptionClient implements BbsTranscriptionClient {
  private final String key, model;
  private final HttpClient http;
  private final URI endpoint;
  private final ObjectMapper json = new ObjectMapper();

  @Autowired
  public OpenAiBbsTranscriptionClient(
      @Value("${app.bbs.voice.api-key:${OPENAI_API_KEY:}}") String key,
      @Value("${app.bbs.voice.model:${OPENAI_STT_MODEL:gpt-transcribe}}") String model) {
    this(key, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        URI.create("https://api.openai.com/v1/audio/transcriptions"));
  }

  // Local HTTP endpoint injection is available only to package tests, never through user input.
  OpenAiBbsTranscriptionClient(String key, String model, HttpClient http, URI endpoint) {
    this.key = key.strip(); this.model = model.strip(); this.http = http; this.endpoint = endpoint;
  }

  @Override public boolean configured() {
    return !key.isBlank() && model.matches("gpt-[a-z0-9.-]{1,80}");
  }

  @Override public String transcribe(byte[] audio, String mimeType) {
    if (!configured()) throw BbsVoiceException.notConfigured();
    String boundary = "bbs-" + UUID.randomUUID();
    var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + key)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, audio, mimeType))).build();
    var response = http.sendAsync(request, ignored -> new LimitedBody());
    try {
      var received = response.get(30, TimeUnit.SECONDS);
      int status = received.statusCode();
      if (status == 401 || status == 403) throw BbsVoiceException.notConfigured();
      if (status == 429) throw BbsVoiceException.limited();
      if (status != 200) throw BbsVoiceException.unavailable();
      var body = json.readTree(received.body());
      if (body == null || !body.path("text").isTextual()) throw BbsVoiceException.unavailable();
      String transcript = body.path("text").textValue().strip();
      if (transcript.isEmpty()) throw new BbsVoiceException(HttpStatus.UNPROCESSABLE_ENTITY,
          "BBS_STT_EMPTY", "게임 이름이 잘 들리지 않았어요. 다시 말씀해 주세요.");
      if (transcript.length() > 255) throw new BbsVoiceException(HttpStatus.UNPROCESSABLE_ENTITY,
          "BBS_STT_TOO_LONG", "게임 이름과 요청을 짧게 말씀해 주세요.");
      return transcript;
    } catch (TimeoutException e) {
      throw new BbsVoiceException(HttpStatus.GATEWAY_TIMEOUT, "BBS_STT_TIMEOUT", "음성 인식이 지연되고 있어요. 다시 시도해 주세요.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw BbsVoiceException.unavailable();
    } catch (ExecutionException | IOException e) {
      throw BbsVoiceException.unavailable();
    } finally {
      response.cancel(true);
    }
  }

  private byte[] multipart(String boundary, byte[] audio, String mimeType) {
    String extension = switch (mimeType) {
      case "audio/webm" -> "webm";
      case "audio/mp4" -> "mp4";
      case "audio/wav" -> "wav";
      default -> throw BbsVoiceException.invalidAudio();
    };
    var bytes = new ByteArrayOutputStream();
    field(bytes, boundary, "model", model);
    field(bytes, boundary, "response_format", "json");
    field(bytes, boundary, "prompt", "Korean or English game names and spoken requests to search or hear the latest official game news. Transcribe the full request, preserving spoken sequel numbers and editions. Do not add a number or edition that was not spoken.");
    if (model.startsWith("gpt-transcribe")) {
      field(bytes, boundary, "languages[]", "ko");
      field(bytes, boundary, "languages[]", "en");
    }
    append(bytes, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech." + extension + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n");
    bytes.writeBytes(audio);
    append(bytes, "\r\n--" + boundary + "--\r\n");
    return bytes.toByteArray();
  }
  private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
    append(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
  }
  private static void append(ByteArrayOutputStream out, String text) { out.writeBytes(text.getBytes(StandardCharsets.UTF_8)); }

  /** Bound response memory and let the outer deadline cover the entire response body. */
  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;
    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
    @Override public void onNext(List<ByteBuffer> items) {
      for (var item : items) {
        if (bytes.size() + item.remaining() > 65536) {
          subscription.cancel(); result.completeExceptionally(new IOException("STT response too large")); return;
        }
        byte[] chunk = new byte[item.remaining()]; item.get(chunk); bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }
    @Override public void onError(Throwable error) { result.completeExceptionally(error); }
    @Override public void onComplete() { result.complete(bytes.toByteArray()); }
  }
}
