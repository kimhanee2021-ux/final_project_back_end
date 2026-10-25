package org.ict.final_project_back_end.domain.gamenews.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.ict.final_project_back_end.domain.gamenews.client.BbsSpeechClient;
import org.ict.final_project_back_end.domain.gamenews.client.BbsSpeechClient.Speech;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

@Service
public class BbsAudioService {
  private final BbsBriefingService briefings;
  private final BbsSpeechClient client;
  private final boolean enabled;
  private final BbsVoiceRequestGuard limits;
  private final Clock clock;
  private final LinkedHashMap<String,Cached> cache=new LinkedHashMap<>(16,.75f,true);
  private final Set<String> pending=new HashSet<>();
  private record Cached(Speech speech,Instant expires) {}
  @Autowired
  public BbsAudioService(BbsBriefingService briefings,BbsSpeechClient client,
      @Value("${app.bbs.audio.enabled:${BBS_AUDIO_ENABLED:true}}") boolean enabled,
      @Value("${app.bbs.audio.daily-calls:100}") int daily,
      @Value("${app.bbs.audio.calls-per-minute:3}") int perMinute,
      @Value("${app.bbs.audio.concurrent-calls:2}") int concurrent) {
    this(briefings,client,enabled,new BbsVoiceRequestGuard(daily,perMinute,concurrent),Clock.systemUTC());
  }
  BbsAudioService(BbsBriefingService briefings,BbsSpeechClient client,boolean enabled,BbsVoiceRequestGuard limits,Clock clock) {
    this.briefings=briefings;this.client=client;this.enabled=enabled;this.limits=limits;this.clock=clock;
  }
  public Speech generate(long appId,String sourceId,String sourceHash,Instant generatedAt,String address) {
    if (generatedAt==null) throw BbsAudioException.invalid();
    if (!enabled) throw BbsAudioException.notReady();
    // This GET verifies the game, official source and saved GPT version. It never generates or writes.
    var saved=briefings.get(appId,sourceId,sourceHash);
    if (!saved.status().equals("READY") || !saved.briefing().generatedAt().equals(generatedAt)) throw BbsAudioException.changed();
    String script=saved.briefing().narrationScript();
    String key=hash(appId+":"+sourceId+":"+sourceHash+":"+script);
    synchronized(cache) {
      cache.values().removeIf(entry -> !entry.expires().isAfter(clock.instant()));
      var hit=cache.get(key);if (hit!=null) return hit.speech();
      if (!client.configured()) throw BbsAudioException.notReady();
      if (!pending.add(key)) throw BbsAudioException.busy();
    }
    try (var permit=acquire(address)) {
      Speech speech=client.generate(script);
      synchronized(cache) {
        cache.put(key,new Cached(speech,clock.instant().plus(Duration.ofMinutes(30))));
        while(cache.size()>8) cache.remove(cache.keySet().iterator().next());
      }
      return speech;
    } finally { synchronized(cache) { pending.remove(key); } }
  }
  private BbsVoiceRequestGuard.Permit acquire(String address) {
    try { return limits.acquire(address); } catch (BbsVoiceException ex) { throw BbsAudioException.limited(); }
  }
  private String hash(String text) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
  }
}
