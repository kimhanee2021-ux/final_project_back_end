package org.ict.final_project_back_end.domain.aiWorldcup;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiWorldcup.service.GeminiWorldcupClient;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GeminiWorldcupTest {
  HttpServer server;AtomicInteger calls=new AtomicInteger();int status=200;boolean temporary503=false;String finish="STOP";GeminiWorldcupClient client;
  @BeforeEach void start() throws Exception {
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      exchange.getRequestBody().readAllBytes();int count=calls.incrementAndGet();
      var mapper=new ObjectMapper();var response=Map.of("candidates",List.of(Map.of("finishReason",finish,"content",Map.of("parts",List.of(Map.of("text","{\"explanation\":\"비교\",\"pairIds\":[1,2],\"summary\":\"\",\"traits\":[],\"evidence\":[],\"uncertainty\":\"\",\"favoriteIds\":[]}"))))));
      byte[] body=mapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(temporary503&&count==1?503:status,body.length);exchange.getResponseBody().write(body);exchange.close();
    });server.start();client=new GeminiWorldcupClient("test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort());
  }
  @AfterEach void stop(){server.stop(0);}
  @Test void retriesTemporary503ThenAcceptsStructuredResponse(){temporary503=true;assertEquals(2,client.generateWorldcup(Map.of("complete",false)).path("pairIds").size());assertEquals(2,calls.get());}
  @Test void neverRetriesQuotaFailure(){status=429;assertThrows(AiException.class,()->client.generateWorldcup(Map.of()));assertEquals(1,calls.get());}
  @Test void boundsRetriesAndReportsBusy(){status=503;var error=assertThrows(AiException.class,()->client.generateWorldcup(Map.of()));assertTrue(error.getMessage().contains("혼잡"));assertEquals(3,calls.get());}
  @Test void rejectsUnfinishedGeneration(){finish="MAX_TOKENS";assertThrows(AiException.class,()->client.generateWorldcup(Map.of()));}
}
