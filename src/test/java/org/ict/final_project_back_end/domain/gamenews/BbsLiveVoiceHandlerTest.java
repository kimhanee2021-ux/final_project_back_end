package org.ict.final_project_back_end.domain.gamenews;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.ict.final_project_back_end.domain.gamenews.client.BbsLiveTranscriptionClient;
import org.ict.final_project_back_end.domain.gamenews.controller.BbsLiveVoiceHandler;
import org.ict.final_project_back_end.domain.gamenews.service.BbsVoiceRequestGuard;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import static org.mockito.Mockito.*;

class BbsLiveVoiceHandlerTest {
  WebSocketSession socket(String id) {
    var s=mock(WebSocketSession.class);when(s.getId()).thenReturn(id);when(s.isOpen()).thenReturn(true);
    when(s.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1",9999));return s;
  }
  @Test void providerReadyAudioCommitAndDisconnectCloseBothSidesAndReleasePermit() throws Exception {
    var client=mock(BbsLiveTranscriptionClient.class);when(client.configured()).thenReturn(true);
    var stream=mock(BbsLiveTranscriptionClient.Stream.class);var events=new AtomicReference<BbsLiveTranscriptionClient.Events>();
    when(client.open(any())).thenAnswer(call -> {events.set(call.getArgument(0));events.get().ready();return stream;});
    var handler=new BbsLiveVoiceHandler(client,new BbsVoiceRequestGuard(10,10,1));
    try {
      var s=socket("one");handler.afterConnectionEstablished(s);
      verify(s,timeout(2000)).sendMessage(argThat(m -> m instanceof TextMessage text && text.getPayload().contains("ready")));
      handler.handleMessage(s,new BinaryMessage(new byte[4800]));verify(stream).append(argThat(b -> b.length==4800));
      handler.handleMessage(s,new TextMessage("{\"type\":\"commit\"}"));verify(stream).commit();
      events.get().completed("멈춰");verify(stream).close();verify(s).close(CloseStatus.NORMAL);
      var other=socket("two");handler.afterConnectionEstablished(other);verify(client,timeout(2000).times(2)).open(any());
      handler.afterConnectionClosed(other,CloseStatus.NORMAL);verify(stream,timeout(2000).times(2)).close();
    } finally {handler.shutdown();}
  }
  @Test void clientCannotForwardProviderCommandsAndClosedConnectionsRejectLateUpstream() throws Exception {
    var client=mock(BbsLiveTranscriptionClient.class);when(client.configured()).thenReturn(true);
    var gate=new java.util.concurrent.CountDownLatch(1);var stream=mock(BbsLiveTranscriptionClient.Stream.class);
    when(client.open(any())).thenAnswer(call -> {gate.await();return stream;});
    var handler=new BbsLiveVoiceHandler(client,new BbsVoiceRequestGuard(10,10,1));var s=socket("one");
    try {
      handler.afterConnectionEstablished(s);verify(client,timeout(2000)).open(any());
      handler.handleMessage(s,new TextMessage("{\"type\":\"session.update\",\"model\":\"other\"}"));verify(s).close(CloseStatus.NORMAL);
      gate.countDown();verify(stream,timeout(2000)).close();
      verify(stream,never()).append(any());verify(stream,never()).commit();
    } finally {gate.countDown();handler.shutdown();}
  }
}
