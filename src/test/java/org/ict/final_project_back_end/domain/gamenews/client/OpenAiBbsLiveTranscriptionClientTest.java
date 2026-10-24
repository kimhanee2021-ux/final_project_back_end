package org.ict.final_project_back_end.domain.gamenews.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiBbsLiveTranscriptionClientTest {
  static class Events implements BbsLiveTranscriptionClient.Events {
    List<String> partials=new ArrayList<>(), finals=new ArrayList<>(); int ready,failed;
    public void ready() { ready++; }
    public void partial(String text) { partials.add(text); }
    public void completed(String text) { finals.add(text); }
    public void failed() { failed++; }
  }
  OpenAiBbsLiveTranscriptionClient client() {
    return new OpenAiBbsLiveTranscriptionClient("test","gpt-live-transcribe",HttpClient.newHttpClient(),URI.create("ws://localhost:1"));
  }
  void event(OpenAiBbsLiveTranscriptionClient.LiveStream stream,Object value) throws Exception { stream.accept(OpenAiBbsSpeechClientTest.JSON.writeValueAsString(value)); }
  @Test void streamingDeltasPrecedeCommitAndFinalCompletesOnceWithoutSavingAudio() throws Exception {
    var e=new Events(); var stream=client().new LiveStream(e); var socket=new OpenAiBbsSpeechClientTest.Socket(); stream.onOpen(socket);
    assertEquals("transcription",socket.sent.getFirst().at("/session/type").asText());
    assertEquals("gpt-live-transcribe",socket.sent.getFirst().at("/session/audio/input/transcription/model").asText());
    assertTrue(socket.sent.getFirst().at("/session/audio/input/turn_detection").isNull());
    event(stream,Map.of("type","session.updated")); stream.append(new byte[4800]);
    event(stream,Map.of("type","conversation.item.input_audio_transcription.delta","item_id","one","delta","다음 소식"));
    assertEquals(List.of("다음 소식"),e.partials); assertTrue(e.finals.isEmpty());
    stream.commit(); assertEquals("input_audio_buffer.commit",socket.sent.getLast().path("type").asText());
    event(stream,Map.of("type","conversation.item.input_audio_transcription.completed","item_id","one","transcript","다음 소식 들려줘"));
    assertEquals(List.of("다음 소식 들려줘"),e.finals); assertTrue(socket.aborted); assertEquals(0,e.failed);
    event(stream,Map.of("type","conversation.item.input_audio_transcription.completed","item_id","one","transcript","late"));
    assertEquals(1,e.finals.size());
  }
  @Test void prematureCompletionDifferentItemsAndOversizeResponsesFailClosed() throws Exception {
    for (int scenario=0;scenario<4;scenario++) {
      var e=new Events(); var stream=client().new LiveStream(e); var socket=new OpenAiBbsSpeechClientTest.Socket(); stream.onOpen(socket);
      event(stream,Map.of("type","session.updated")); stream.append(new byte[4800]);
      if (scenario!=0) stream.commit();
      if (scenario==1) event(stream,Map.of("type","conversation.item.input_audio_transcription.delta","item_id","first","delta","다음"));
      if (scenario==3) stream.onText(socket,"x".repeat(16385),false);
      else event(stream,Map.of("type","conversation.item.input_audio_transcription.completed","item_id","other","transcript",scenario==2?"x".repeat(256):"멈춰"));
      assertEquals(1,e.failed); assertTrue(socket.aborted); assertTrue(e.finals.isEmpty());
    }
  }
  @Test void audioIsBoundedAndCancellationCannotCompleteOrLeaveSocketOpen() throws Exception {
    var e=new Events(); var stream=client().new LiveStream(e); var socket=new OpenAiBbsSpeechClientTest.Socket(); stream.onOpen(socket);
    event(stream,Map.of("type","session.updated"));
    for(int i=0;i<301;i++) stream.append(new byte[4800]);
    assertEquals(1,e.failed); assertTrue(socket.aborted);
    e=new Events(); stream=client().new LiveStream(e); socket=new OpenAiBbsSpeechClientTest.Socket(); stream.onOpen(socket);stream.close();
    event(stream,Map.of("type","session.updated")); assertEquals(0,e.ready); assertTrue(socket.aborted);
  }
  @Test void malformedAudioDuplicateCommitAndUnexpectedCloseReleaseResources() throws Exception {
    for (int scenario=0;scenario<4;scenario++) {
      var e=new Events(); var stream=client().new LiveStream(e); var socket=new OpenAiBbsSpeechClientTest.Socket(); stream.onOpen(socket);
      event(stream,Map.of("type","session.updated"));
      if(scenario==0) stream.append(new byte[3]);
      if(scenario==1) stream.commit();
      if(scenario==2) {stream.append(new byte[4800]);stream.commit();stream.commit();}
      if(scenario==3) stream.onClose(socket,1000,"unexpected");
      assertEquals(1,e.failed);assertTrue(socket.aborted);
    }
  }
}
