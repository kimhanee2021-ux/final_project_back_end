package org.ict.final_project_back_end.domain.gamenews.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ict.final_project_back_end.domain.gamenews.client.BbsLiveTranscriptionClient;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.ict.final_project_back_end.domain.gamenews.service.BbsVoiceRequestGuard;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class BbsLiveVoiceHandler extends AbstractWebSocketHandler {
  private static final ObjectMapper JSON=new ObjectMapper();
  private final BbsLiveTranscriptionClient client;
  private final BbsVoiceRequestGuard limits;
  private final ConcurrentMap<String, Call> calls=new ConcurrentHashMap<>();
  private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread=new Thread(r,"bbs-live-timeout"); thread.setDaemon(true); return thread;
  });
  public BbsLiveVoiceHandler(BbsLiveTranscriptionClient client, BbsVoiceRequestGuard limits) {
    this.client=client; this.limits=limits;
  }
  @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    session.setBinaryMessageSizeLimit(12000); session.setTextMessageSizeLimit(128);
    BbsVoiceRequestGuard.Permit permit;
    try {
      if (!client.configured()) throw BbsVoiceException.notConfigured();
      permit=limits.acquire(session.getRemoteAddress()==null?"unknown":session.getRemoteAddress().getAddress().getHostAddress());
    } catch (BbsVoiceException ex) {
      session.sendMessage(new TextMessage(JSON.writeValueAsString(Map.of("type","error","message",ex.getMessage()))));
      session.close(CloseStatus.NORMAL); return;
    }
    Call call=new Call(session,permit); calls.put(session.getId(),call);
    call.timeout=timer.schedule(() -> call.error("음성 입력 시간이 초과됐어요. 다시 말씀해 주세요."),55,TimeUnit.SECONDS);
    Thread.startVirtualThread(() -> {
      try {
        var stream=client.open(call);
        call.upstream=stream;
        if (call.closed.get()) stream.close(); else call.notifyReady();
      } catch (Exception ex) { call.error("실시간 음성 인식을 연결하지 못했어요. 잠시 후 다시 시도해 주세요."); }
    });
  }
  @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    Call call=calls.get(session.getId()); if (call==null) return;
    if (!call.ready || call.committed || !message.isLast()) { call.error("음성 연결 순서가 올바르지 않아요. 다시 시도해 주세요."); return; }
    byte[] pcm=new byte[message.getPayloadLength()]; message.getPayload().get(pcm);
    call.upstream.append(pcm);
  }
  @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    Call call=calls.get(session.getId()); if (call==null) return;
    try {
      var event=JSON.readTree(message.getPayload());
      if (event.size()!=1 || !event.path("type").asText().equals("commit") || !call.ready || call.committed) throw new IllegalArgumentException();
      call.committed=true; call.upstream.commit();
    } catch (Exception ex) { call.error("음성 입력을 마무리하지 못했어요. 다시 시도해 주세요."); }
  }
  @Override public void handleTransportError(WebSocketSession session, Throwable exception) { close(session); }
  @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { close(session); }
  private void close(WebSocketSession session) { Call call=calls.get(session.getId()); if (call!=null) call.close(); }
  @PreDestroy public void shutdown() { calls.values().forEach(Call::close); timer.shutdownNow(); }

  private final class Call implements BbsLiveTranscriptionClient.Events {
    final WebSocketSession session;
    final BbsVoiceRequestGuard.Permit permit;
    final AtomicBoolean closed=new AtomicBoolean();
    volatile BbsLiveTranscriptionClient.Stream upstream;
    volatile ScheduledFuture<?> timeout;
    volatile boolean providerReady, ready, committed;
    Call(WebSocketSession session,BbsVoiceRequestGuard.Permit permit) { this.session=session; this.permit=permit; }
    @Override public void ready() { providerReady=true; notifyReady(); }
    synchronized void notifyReady() {
      if (!closed.get() && providerReady && upstream!=null && !ready) { ready=true; send("ready",""); }
    }
    @Override public void partial(String text) { send("partial",text); }
    @Override public void completed(String text) { send("final",text); close(); }
    @Override public void failed() { error("음성을 인식하지 못했어요. 다시 말씀해 주세요."); }
    void error(String message) { send("error",message); close(); }
    void send(String type,String text) {
      try {
        synchronized(session) {
          if (closed.get() || !session.isOpen()) return;
          session.sendMessage(new TextMessage(JSON.writeValueAsString(Map.of("type",type,type.equals("error")?"message":"text",text))));
        }
      } catch (IOException ex) { close(); }
    }
    void close() {
      if (!closed.compareAndSet(false,true)) return;
      calls.remove(session.getId(),this);
      if (timeout!=null) timeout.cancel(false);
      if (upstream!=null) upstream.close();
      permit.close();
      try { session.close(CloseStatus.NORMAL); } catch (IOException ignored) { }
    }
  }
}
