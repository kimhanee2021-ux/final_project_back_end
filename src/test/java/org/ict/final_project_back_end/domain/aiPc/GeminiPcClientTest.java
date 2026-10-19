package org.ict.final_project_back_end.domain.aiPc;
import com.sun.net.httpserver.HttpServer;import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import org.ict.final_project_back_end.domain.aiPc.service.GeminiPcClient;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;

class GeminiPcClientTest {
 HttpServer server;GeminiPcClient client;String reply;int status;String received;
 @BeforeEach void setup()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{received=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);var bytes=reply.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();client=new GeminiPcClient("test-only","test-model","http://127.0.0.1:"+server.getAddress().getPort());status=200;}
 @AfterEach void stop(){server.stop(0);}
 String answer(String ram)throws Exception{String text="{\"cpuModel\":\"i7-13700\",\"cpuDetails\":\"\",\"gpuModel\":\"Intel UHD Graphics 770\",\"gpuMemory\":\"128MB 표시값\",\"ramGB\":\""+ram+"\",\"ramSpeed\":\"3200 MT/s\",\"storageTotalGB\":\"\",\"storageFreeGB\":\"\",\"os\":\"\",\"warnings\":[\"공유 그래픽 메모리 확인 필요\"]}";return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"+new ObjectMapper().writeValueAsString(text)+"}]}}]}";}
 @Test void extractsOnlyKnownFieldsAndKeepsMissingNull()throws Exception{reply=answer("32");var result=client.extract(new byte[]{1},"image/png");assertEquals(32.0,result.specs().ramGB());assertNull(result.specs().storageFreeGB());assertTrue(received.contains("inlineData"));assertTrue(received.contains("장치ID"));}
 @Test void rejectsInventedOrNonFiniteCapacity()throws Exception{reply=answer("NaN");assertThrows(AiException.class,()->client.extract(new byte[]{1},"image/png"));}
 @Test void rateLimitIsNotReturnedAsSuccess(){status=429;reply="{}";assertThrows(AiException.class,()->client.extract(new byte[]{1},"image/png"));}
 @Test void truncatedResponseIsNotApplied(){reply="{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}";assertThrows(AiException.class,()->client.extract(new byte[]{1},"image/png"));}
 @Test void settingsCannotInventMeasuredFps()throws Exception{var data=new ObjectMapper().readTree("{\"settings\":[\"옵션을 낮추면 60 FPS 보장\"]}");assertThrows(AiException.class,()->client.list(data,"settings"));}
 @Test void ignoresAiCalculatedStorageWithoutRawReading()throws Exception{var json=new ObjectMapper();var outer=json.readTree(answer("32"));var inner=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(outer.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText());inner.put("storageTotalGB","1413");inner.put("storageFreeGB","1392");inner.put("storageTotalText","");inner.put("storageUsedText","");inner.put("storageFreeText","");((com.fasterxml.jackson.databind.node.ObjectNode)outer.path("candidates").get(0).path("content").path("parts").get(0)).put("text",json.writeValueAsString(inner));reply=outer.toString();var result=client.extract(new byte[]{1},"image/png");assertNull(result.specs().storageTotalGB());assertNull(result.specs().storageFreeGB());}
}
