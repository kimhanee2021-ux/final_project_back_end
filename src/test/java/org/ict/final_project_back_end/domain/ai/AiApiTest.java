package org.ict.final_project_back_end.domain.ai;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.security.JwtService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class AiApiTest {
  static HttpServer server;
  static volatile int externalStatus = 200;
  static volatile String reply;
  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        byte[] body = reply.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(externalStatus, body.length);
        exchange.getResponseBody().write(body); exchange.close();
      }); server.start();
    } catch(Exception e) { throw new RuntimeException(e); }
  }
  @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
    r.add("app.ai.base-url", () -> "http://127.0.0.1:"+server.getAddress().getPort());
    r.add("app.ai.api-key", () -> "test-key-only");
  }
  @Autowired MockMvc mvc; @Autowired GameRepository games; @Autowired UserRepository users; @Autowired JwtService jwt;
  String token;
  static final String REQUEST = "{\"mode\":\"recommend\",\"message\":\"RPG 게임 추천해줘\",\"genre\":\"RPG\",\"history\":[]}";
  @BeforeEach void setup() {
    externalStatus=200;
    reply="{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"answer\\\":\\\"취향에 맞는 게임입니다.\\\",\\\"recommendations\\\":[{\\\"appId\\\":\\\"42\\\",\\\"reason\\\":\\\"RPG 장르\\\"}]}\"}]}}]}";
    var user=users.save(User.builder().email(java.util.UUID.randomUUID()+"@example.invalid").nickname(java.util.UUID.randomUUID().toString()).passwordHash("test").build());
    token="Bearer "+jwt.generateToken(user.getId(),user.getEmail());
    games.save(Game.builder().appId(42L).name("실제 RPG").genre("RPG").description("스토리 중심").priceInfo("₩20,000").positiveRate(new BigDecimal("90.00")).build());
  }
  @AfterAll static void stop(){server.stop(0);}
  @Test void recommendationUsesCatalogFields() throws Exception {
    chat(REQUEST).andExpect(status().isOk()).andExpect(jsonPath("$.games[0].name").value("실제 RPG"))
        .andExpect(jsonPath("$.games[0].priceInfo").value("₩20,000"))
        .andExpect(jsonPath("$.games[0].storeUrl").value("https://store.steampowered.com/app/42/"));
  }
  @Test void fabricatedIdIsRejected() throws Exception {
    reply=reply.replace("42", "99999"); chat(REQUEST).andExpect(status().isBadGateway());
  }
  @Test void malformedReplyIsHandled() throws Exception {reply="{}";chat(REQUEST).andExpect(status().isBadGateway());}
  @Test void quotaErrorHasSafeMessage() throws Exception {externalStatus=429;reply="secret upstream diagnostic";chat(REQUEST).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").exists());}
  @Test void upstreamFailureIsHandled() throws Exception {externalStatus=500;reply="secret upstream diagnostic";chat(REQUEST).andExpect(status().isBadGateway());}
  @Test void blockedResponseIsHandled() throws Exception {reply="{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";chat(REQUEST).andExpect(status().isBadGateway());}
  @Test void purchaseUsesSelectedGame() throws Exception {chat("{\"mode\":\"purchase\",\"message\":\"살만해?\",\"gameId\":42}").andExpect(status().isOk()).andExpect(jsonPath("$.games[0].appId").value(42));}
  @Test void missingGameIs404() throws Exception {chat("{\"mode\":\"purchase\",\"message\":\"살만해?\",\"gameId\":999}").andExpect(status().isNotFound());}
  @Test void invalidInputAndMissingSelectionAre400() throws Exception {
    chat("{\"mode\":\"recommend\",\"message\":\" \"}").andExpect(status().isBadRequest());
    chat("{\"mode\":\"purchase\",\"message\":\"살만해?\"}").andExpect(status().isBadRequest());
  }
  @Test void noCatalogMatchesReturnsClarification() throws Exception {chat(REQUEST.replace("RPG", "없는장르")).andExpect(status().isOk()).andExpect(jsonPath("$.games").isEmpty());}
  @Test void requiresLogin() throws Exception {mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isUnauthorized());}
  @Test void searchFindsOnlyCatalogGames() throws Exception {mvc.perform(get("/api/ai/games?keyword=실제").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("실제 RPG"));}
  org.springframework.test.web.servlet.ResultActions chat(String body) throws Exception {return mvc.perform(post("/api/ai/chat").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body));}
}
