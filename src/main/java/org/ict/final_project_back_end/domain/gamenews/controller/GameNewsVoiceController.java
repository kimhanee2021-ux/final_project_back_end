package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.*;
import java.util.UUID;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.*;
import org.ict.final_project_back_end.domain.gamenews.service.GameNewsVoiceIntentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/news/voice-intent")
public class GameNewsVoiceController {
  private final GameNewsVoiceIntentService service;
  public GameNewsVoiceController(GameNewsVoiceIntentService service) { this.service = service; }
  @PostMapping public ResponseEntity<Response> interpret(@RequestBody Request body, HttpServletRequest request,
      HttpServletResponse response, @CookieValue(name = "gs_news_voice", required = false) String cookie) {
    String owner = cookie != null && cookie.matches("[a-f0-9-]{36}") ? cookie : UUID.randomUUID().toString();
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("gs_news_voice", owner).httpOnly(true)
        .secure(request.isSecure()).sameSite("Strict").path("/api/games/news/voice-intent").build().toString());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.interpret(body, owner, request.getRemoteAddr()));
  }
}
