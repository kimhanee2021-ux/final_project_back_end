package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.*;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsAudioException;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiBbsSpeechClientTest {
  static final ObjectMapper JSON=new ObjectMapper();
  static final String SCRIPT="게임의 공식 소식입니다. 이번 업데이트에서 맵과 소리 관련 기능을 개선했습니다. 자세한 내용은 공식 소식을 확인해 주세요. ".repeat(4);
  static class Socket implements WebSocket {
    final List<JsonNode> sent=new ArrayList<>(); boolean aborted;
    public CompletableFuture<WebSocket> sendText(CharSequence text,boolean last) {
      try { sent.add(JSON.readTree(text.toString()));return CompletableFuture.completedFuture(this); }
      catch(Exception ex) { return CompletableFuture.failedFuture(ex); }
    }
    public void request(long count) {}
    public void abort() { aborted=true; }
    public boolean isInputClosed() { return aborted; }
    public boolean isOutputClosed() { return aborted; }
    public String getSubprotocol() { return ""; }
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer bytes,boolean last) { throw new UnsupportedOperationException(); }
    public CompletableFuture<WebSocket> sendPing(ByteBuffer bytes) { throw new UnsupportedOperationException(); }
    public CompletableFuture<WebSocket> sendPong(ByteBuffer bytes) { throw new UnsupportedOperationException(); }
    public CompletableFuture<WebSocket> sendClose(int code,String reason) { aborted=true;return CompletableFuture.completedFuture(this); }
  }
  static void event(OpenAiBbsSpeechClient.SpeechStream stream,Socket socket,Object value) throws Exception { stream.onText(socket,JSON.writeValueAsString(value),true); }
  static void start(OpenAiBbsSpeechClient.SpeechStream stream,Socket socket) throws Exception {
    stream.onOpen(socket);event(stream,socket,Map.of("type","session.created"));event(stream,socket,Map.of("type","session.updated"));
  }
  static void pcm(OpenAiBbsSpeechClient.SpeechStream stream,Socket socket,int seconds) throws Exception {
    byte[] chunk=new byte[48000];for(int i=0;i<seconds;i++) event(stream,socket,Map.of("type","response.output_audio.delta","delta",Base64.getEncoder().encodeToString(chunk)));
  }
  static void done(OpenAiBbsSpeechClient.SpeechStream stream,Socket socket,String transcript,String status) throws Exception {
    event(stream,socket,Map.of("type","response.output_audio.done"));
    event(stream,socket,Map.of("type","response.output_audio_transcript.done","transcript",transcript));
    event(stream,socket,Map.of("type","response.done","response",Map.of("status",status)));
  }
  OpenAiBbsSpeechClient.SpeechStream stream() { return new OpenAiBbsSpeechClient.SpeechStream(SCRIPT,"gpt-realtime-2.1-mini","marin"); }
  @Test void sessionSendsOnlySavedTextAndCompletesValidPcmAsWav() throws Exception {
    var stream=stream();var socket=new Socket();start(stream,socket);
    assertEquals(3,socket.sent.size());var session=socket.sent.get(0).path("session");
    assertEquals("marin",session.at("/audio/output/voice").asText());assertTrue(session.at("/audio/input/turn_detection").isNull());
    assertEquals(24000,session.at("/audio/output/format/rate").asInt());assertEquals(4096,session.path("max_output_tokens").asInt());
    assertEquals(SCRIPT,socket.sent.get(1).at("/item/content/0/text").asText());assertEquals("response.create",socket.sent.get(2).path("type").asText());
    pcm(stream,socket,35);done(stream,socket,SCRIPT,"completed");var speech=stream.result.join();
    assertEquals(35,speech.seconds());assertEquals(44+48000*35,speech.wav().length);
    var bytes=ByteBuffer.wrap(speech.wav()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    assertEquals(24000,bytes.getInt(24));assertEquals(48000*35,bytes.getInt(40));assertEquals("RIFF",new String(speech.wav(),0,4,java.nio.charset.StandardCharsets.US_ASCII));
  }
  @Test void fragmentedMessagesWorkAndIncompleteOrRewrittenAudioIsDiscarded() throws Exception {
    var stream=stream();var socket=new Socket();stream.onOpen(socket);
    stream.onText(socket,"{\"type\":\"session.",false);stream.onText(socket,"created\"}",true);assertEquals(1,socket.sent.size());
    for(String status:List.of("failed","incomplete","cancelled")) {
      stream=stream();socket=new Socket();start(stream,socket);pcm(stream,socket,30);done(stream,socket,SCRIPT,status);assertTrue(stream.result.isCompletedExceptionally());
    }
    stream=stream();socket=new Socket();start(stream,socket);pcm(stream,socket,30);done(stream,socket,"죄송하지만 답변할 수 없습니다.","completed");assertTrue(stream.result.isCompletedExceptionally());
  }
  @Test void boundsMalformedBase64EmptyAudioAndUnexpectedCloseAreRejected() throws Exception {
    var stream=stream();var socket=new Socket();start(stream,socket);pcm(stream,socket,91);assertTrue(stream.result.isCompletedExceptionally());assertTrue(socket.aborted);
    stream=stream();socket=new Socket();start(stream,socket);event(stream,socket,Map.of("type","response.output_audio.delta","delta","INVALID@@"));assertTrue(stream.result.isCompletedExceptionally());
    stream=stream();socket=new Socket();start(stream,socket);done(stream,socket,SCRIPT,"completed");assertTrue(stream.result.isCompletedExceptionally());
    stream=stream();socket=new Socket();stream.onText(socket,"x".repeat(OpenAiBbsSpeechClient.MAX_EVENT+1),false);assertTrue(stream.result.isCompletedExceptionally());
    stream=stream();socket=new Socket();start(stream,socket);stream.onClose(socket,1000,"closed");assertTrue(stream.result.isCompletedExceptionally());
  }
  @Test void timeoutClosesConnectionWithoutAutomaticRetryAndMissingKeyNeverConnects() {
    var socket=new Socket();var count=new java.util.concurrent.atomic.AtomicInteger();
    OpenAiBbsSpeechClient.Connector connector=listener -> { count.incrementAndGet();listener.onOpen(socket);return CompletableFuture.completedFuture(socket); };
    var client=new OpenAiBbsSpeechClient("test","gpt-realtime-2.1-mini","marin",connector,Duration.ofMillis(10));
    assertThrows(BbsAudioException.class,() -> client.generate(SCRIPT));assertTrue(socket.aborted);assertEquals(1,count.get());
    var absent=new OpenAiBbsSpeechClient("","gpt-realtime-2.1-mini","marin",connector,Duration.ofMillis(10));
    assertThrows(BbsAudioException.class,() -> absent.generate(SCRIPT));assertEquals(1,count.get());
  }
  @Test void textComparisonIgnoresSpacingAndPunctuationButRejectsTruncationAndAddedSpeech() {
    assertTrue(OpenAiBbsSpeechClient.faithful(SCRIPT,SCRIPT.replace(" ","").replace(".","!")));
    assertFalse(OpenAiBbsSpeechClient.faithful(SCRIPT,SCRIPT.substring(0,80)));
    assertFalse(OpenAiBbsSpeechClient.faithful(SCRIPT,SCRIPT+"안녕하세요. 추가 소식입니다. ".repeat(25)));
  }
}
