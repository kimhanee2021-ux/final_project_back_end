package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class OpenAiBbsLiveTranscriptionClient implements BbsLiveTranscriptionClient {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final String key, model;
  private final HttpClient http;
  private final URI endpoint;
  @Autowired
  public OpenAiBbsLiveTranscriptionClient(
      @Value("${app.bbs.voice.api-key:${OPENAI_API_KEY:}}") String key,
      @Value("${app.bbs.voice.live-model:${OPENAI_LIVE_STT_MODEL:gpt-live-transcribe}}") String model) {
    this(key, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        URI.create("wss://api.openai.com/v1/realtime?intent=transcription"));
  }
  OpenAiBbsLiveTranscriptionClient(String key, String model, HttpClient http, URI endpoint) {
    this.key=key.strip(); this.model=model.strip(); this.http=http; this.endpoint=endpoint;
  }
  @Override public boolean configured() { return !key.isBlank() && model.equals("gpt-live-transcribe"); }
  @Override public Stream open(Events events) {
    if (!configured()) throw BbsVoiceException.notConfigured();
    var stream = new LiveStream(events);
    var pending = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
        .header("Authorization", "Bearer " + key).buildAsync(endpoint, stream);
    try {
      pending.get(9, TimeUnit.SECONDS);
      return stream;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt(); stream.close(); throw BbsVoiceException.unavailable();
    } catch (ExecutionException | TimeoutException ex) {
      stream.close(); throw BbsVoiceException.unavailable();
    } finally { pending.cancel(true); }
  }
  final class LiveStream implements Stream, WebSocket.Listener {
    private final Events events;
    private final StringBuilder fragment = new StringBuilder(), transcript = new StringBuilder();
    private WebSocket socket;
    private boolean closed, ready, committed;
    private int bytes;
    private String itemId;
    LiveStream(Events events) { this.events=events; }
    @Override public synchronized void onOpen(WebSocket ws) {
      if (closed) { ws.abort(); return; }
      socket=ws;
      send(Map.of("type", "session.update", "session", Map.of("type", "transcription", "audio", Map.of("input", input()))));
      ws.request(1);
    }
    private Map<String,Object> input() {
      Map<String,Object> input=new HashMap<>();
      input.put("format", Map.of("type", "audio/pcm", "rate", 24000));
      input.put("transcription", Map.of("model", model));
      input.put("turn_detection", null);
      return input;
    }
    @Override public synchronized CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      if (closed) return null;
      if (fragment.length()+data.length()>16384) { fail(); return null; }
      fragment.append(data);
      if (last) {
        String value=fragment.toString(); fragment.setLength(0);
        accept(value);
      }
      if (!closed) ws.request(1);
      return null;
    }
    // Separate from transport for protocol/ordering/size validation tests.
    synchronized void accept(String value) {
      if (closed) return;
      try {
        var event=JSON.readTree(value);
        String type=event.path("type").asText();
        if (type.equals("session.updated") && !ready) { ready=true; events.ready(); }
        else if (type.equals("conversation.item.input_audio_transcription.delta")) {
          if (!ready || !sameItem(event.path("item_id").asText()) || !event.path("delta").isTextual()) { fail(); return; }
          transcript.append(event.path("delta").asText());
          if (transcript.length()>1024) { fail(); return; }
          events.partial(transcript.toString());
        } else if (type.equals("conversation.item.input_audio_transcription.completed")) {
          String text=event.path("transcript").asText().strip();
          if (!committed || !sameItem(event.path("item_id").asText()) || text.isBlank() || text.length()>255
              || text.codePoints().anyMatch(Character::isISOControl)) { fail(); return; }
          close(); events.completed(text);
        } else if (type.equals("error") || type.equals("conversation.item.input_audio_transcription.failed")) fail();
      } catch (Exception ex) { fail(); }
    }
    private boolean sameItem(String id) {
      if (id.isBlank()) return false;
      if (itemId==null) itemId=id;
      return itemId.equals(id);
    }
    @Override public synchronized void append(byte[] pcm) {
      if (closed) return;
      if (!ready || committed || pcm.length==0 || pcm.length>12000 || pcm.length%2!=0 || bytes+pcm.length>1440000) {
        fail(); return;
      }
      bytes+=pcm.length;
      send(Map.of("type", "input_audio_buffer.append", "audio", Base64.getEncoder().encodeToString(pcm)));
    }
    @Override public synchronized void commit() {
      if (closed) return;
      if (!ready || committed || bytes<4800) { fail(); return; }
      committed=true;
      send(Map.of("type", "input_audio_buffer.commit"));
    }
    private void send(Map<String,?> event) {
      try {
        // Browser sends at most one 100ms frame at a time; bounded wait prevents an unbounded queue.
        socket.sendText(JSON.writeValueAsString(event),true).get(3,TimeUnit.SECONDS);
      } catch (InterruptedException ex) { Thread.currentThread().interrupt(); fail(); }
      catch (Exception ex) { fail(); }
    }
    private void fail() { if (!closed) { close(); events.failed(); } }
    @Override public synchronized void onError(WebSocket ws, Throwable error) { fail(); }
    @Override public synchronized CompletionStage<?> onClose(WebSocket ws, int status, String reason) { fail(); return null; }
    @Override public synchronized void close() {
      if (closed) return;
      closed=true;
      if (socket!=null) socket.abort();
      fragment.setLength(0); transcript.setLength(0);
    }
  }
}
