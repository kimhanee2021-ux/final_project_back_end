package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.gamenews.service.NewsSpeechService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/news/speech")
public class GameNewsSpeechController {
  public record Request(String text) { }
  private final NewsSpeechService service;
  public GameNewsSpeechController(NewsSpeechService service) { this.service = service; }
  @PostMapping public ResponseEntity<byte[]> speak(@RequestBody Request body, HttpServletRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("audio/wav"))
        .header("X-Content-Type-Options", "nosniff")
        .body(service.speak(body.text(), request.getRemoteAddr()));
  }
}
