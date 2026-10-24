package org.ict.final_project_back_end.domain.gamenews.config;

import org.ict.final_project_back_end.domain.gamenews.controller.BbsLiveVoiceHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class BbsLiveVoiceConfig implements WebSocketConfigurer {
  private final BbsLiveVoiceHandler handler;
  public BbsLiveVoiceConfig(BbsLiveVoiceHandler handler) { this.handler=handler; }
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Same-origin only. Serve /api through the frontend reverse proxy with WebSocket upgrades enabled.
    registry.addHandler(handler,"/api/games/bbs/voice/live");
  }
}
