package org.ict.final_project_back_end.domain.aiGame;

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
  static volatile String received;
  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
  org.springframework.mock.web.MockMultipartFile screenshot() throws Exception {
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", bytes);
    return new org.springframework.mock.web.MockMultipartFile("image", "screen.png", "image/png", bytes.toByteArray());
  }
  void imageReply() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    String text=mapper.writeValueAsString(java.util.Map.of("summary","체력이 낮은 상황입니다.","details",java.util.List.of("체력 표시가 보입니다."),"actions",java.util.List.of("안전한 장소로 이동하세요."),"uncertainty","게임 이름은 확인하기 어렵습니다."));
    reply=mapper.writeValueAsString(java.util.Map.of("candidates",java.util.List.of(java.util.Map.of("finishReason","STOP","content",java.util.Map.of("parts",java.util.List.of(java.util.Map.of("text",text)))))));
  }
  @Test void screenshotSendsImageAndReturnsRealAnalysis() throws Exception {
    imageReply();
    mvc.perform(multipart("/api/ai/screenshots").file(screenshot()).param("type","play").header("Authorization",token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("체력이 낮은 상황입니다."))
        .andExpect(jsonPath("$.isMock").value(false));
    var payload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(received);
    Assertions.assertEquals("image/png",payload.at("/contents/0/parts/1/inlineData/mimeType").asText());
    Assertions.assertArrayEquals(screenshot().getBytes(),java.util.Base64.getDecoder().decode(payload.at("/contents/0/parts/1/inlineData/data").asText()));
  }
  @Test void screenshotNeedsLogin() throws Exception {mvc.perform(multipart("/api/ai/screenshots").file(screenshot()).param("type","play")).andExpect(status().isUnauthorized());}
  @Test void screenshotRejectsFakeImage() throws Exception {
    mvc.perform(multipart("/api/ai/screenshots").file(new org.springframework.mock.web.MockMultipartFile("image","bad.png","image/png","fake".getBytes())).param("type","play").header("Authorization",token)).andExpect(status().isBadRequest());
  }
  @Test void screenshotRejectsUnknownMode() throws Exception {mvc.perform(multipart("/api/ai/screenshots").file(screenshot()).param("type","other").header("Authorization",token)).andExpect(status().isBadRequest());}
  @Test void screenshotRejectsOversize() throws Exception {mvc.perform(multipart("/api/ai/screenshots").file(new org.springframework.mock.web.MockMultipartFile("image","huge.png","image/png",new byte[10*1024*1024+1])).param("type","play").header("Authorization",token)).andExpect(status().isPayloadTooLarge());}
  @Test void screenshotHandlesBlockedAndMalformedReplies() throws Exception {reply="{}";mvc.perform(multipart("/api/ai/screenshots").file(screenshot()).param("type","play").header("Authorization",token)).andExpect(status().isBadGateway());}
  @Test void screenshotQuotaHasSafeMessage() throws Exception {externalStatus=429;reply="secret upstream diagnostic";mvc.perform(multipart("/api/ai/screenshots").file(screenshot()).param("type","error").header("Authorization",token)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").exists());}
  @Test void screenshotRejectsMissingImage() throws Exception {mvc.perform(multipart("/api/ai/screenshots").param("type","item").header("Authorization",token)).andExpect(status().isBadRequest());}
  @Test void screenshotSupportsWebp() throws Exception {
    imageReply();
    byte[] bytes=java.util.Base64.getDecoder().decode("UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoCAAIAAUAmJaACdLoB+AADsAD+8ut//NgVzXPv9//S4P0uD9Lg/9KQAAA=");
    mvc.perform(multipart("/api/ai/screenshots").file(new org.springframework.mock.web.MockMultipartFile("image","game.webp","image/webp",bytes)).param("type","item").header("Authorization",token)).andExpect(status().isOk());
  }
  @Test void screenshotRejectsMimeMismatch() throws Exception {
    mvc.perform(multipart("/api/ai/screenshots").file(new org.springframework.mock.web.MockMultipartFile("image","game.jpg","image/jpeg",screenshot().getBytes())).param("type","item").header("Authorization",token)).andExpect(status().isBadRequest());
  }
  @Test void screenshotRejectsInvalidStructuredAnswer() throws Exception {
    imageReply();reply=reply.replace("summary","invalidField");
    mvc.perform(multipart("/api/ai/screenshots").file(screenshot()).param("type","play").header("Authorization",token)).andExpect(status().isBadGateway());
  }
  org.springframework.test.web.servlet.ResultActions chat(String body) throws Exception {return mvc.perform(post("/api/ai/chat").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body));}
}
