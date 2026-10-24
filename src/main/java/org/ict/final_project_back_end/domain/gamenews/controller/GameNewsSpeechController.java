package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ict.final_project_back_end.domain.gamenews.service.NewsSpeechService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/news/speech")
public class GameNewsSpeechController {
  public record Request(String text) { }
  private final NewsSpeechService service;
  public GameNewsSpeechController(NewsSpeechService service) { this.service = service; }
  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream(@RequestBody Request body, HttpServletRequest request) {
    var emitter = new SseEmitter(60_000L);
    var cancelled = new AtomicBoolean();
    emitter.onCompletion(() -> cancelled.set(true));
    emitter.onTimeout(() -> { cancelled.set(true); emitter.complete(); });
    emitter.onError(error -> cancelled.set(true));
    String caller = request.getRemoteAddr();
    CompletableFuture.runAsync(() -> {
      try {
        service.stream(body.text(), caller, chunk -> {
          try { emitter.send(SseEmitter.event().name("audio").data(Map.of("pcm", Base64.getEncoder().encodeToString(chunk)))); }
          catch (java.io.IOException error) { cancelled.set(true); throw new java.io.UncheckedIOException(error); }
        }, cancelled::get);
        if (!cancelled.get()) emitter.send(SseEmitter.event().name("done").data(Map.of("done", true)));
      } catch (Exception error) {
        if (!cancelled.get()) try {
          String message = error instanceof org.ict.final_project_back_end.global.exception.BusinessException
              ? error.getMessage() : "음성을 준비하지 못했어요. 다시 듣기를 눌러 주세요.";
          emitter.send(SseEmitter.event().name("error").data(Map.of("error", message)));
        } catch (Exception disconnected) { cancelled.set(true); }
      } finally { emitter.complete(); }
    });
    return ResponseEntity.ok().header("Cache-Control", "no-store, no-transform")
        .header("X-Accel-Buffering", "no").header("X-Content-Type-Options", "nosniff").body(emitter);
  }
  @PostMapping public ResponseEntity<byte[]> speak(@RequestBody Request body, HttpServletRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("audio/wav"))
        .header("X-Content-Type-Options", "nosniff")
        .body(service.speak(body.text(), request.getRemoteAddr()));
  }
}
