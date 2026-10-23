package org.ict.final_project_back_end.domain.game.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.ict.final_project_back_end.domain.game.service.GameResolutionService;
import org.ict.final_project_back_end.domain.game.service.GameResolutionService.Request;
import org.ict.final_project_back_end.domain.game.service.GameResolutionService.Response;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/resolve")
public class GameResolutionController {
  private final GameResolutionService service;
  public GameResolutionController(GameResolutionService service) { this.service = service; }
  @PostMapping
  public ResponseEntity<Response> resolve(@RequestBody Request body, HttpServletRequest request,
      HttpServletResponse response, @CookieValue(name = "gs_game_search", required = false) String cookie) {
    String owner = cookie != null && cookie.matches("[a-f0-9-]{36}") ? cookie : UUID.randomUUID().toString();
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("gs_game_search", owner)
        .httpOnly(true).secure(request.isSecure()).sameSite("Strict").path("/api/games/resolve").build().toString());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resolve(body, owner));
  }
}