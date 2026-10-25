package org.ict.final_project_back_end.domain.gamenews.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.ict.final_project_back_end.domain.gamenews.client.BbsTranscriptionClient;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 음성을 텍스트로 변환한다. 게임/뉴스 Entity나 Repository에 쓰지 않는다. */
@Service
public class BbsTranscriptionService {
  public static final int MAX_BYTES = 4_000_000;
  private final BbsTranscriptionClient client;
  private final BbsVoiceRequestGuard guard;
  public BbsTranscriptionService(BbsTranscriptionClient client, BbsVoiceRequestGuard guard) { this.client = client; this.guard = guard; }

  public String transcribe(MultipartFile audio, String address) {
    if (audio == null || audio.getSize() < 12 || audio.getSize() > MAX_BYTES) throw BbsVoiceException.invalidAudio();
    String mime = audio.getContentType() == null ? "" : audio.getContentType().split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    if (!java.util.Set.of("audio/webm", "audio/mp4", "audio/wav").contains(mime)) throw BbsVoiceException.invalidAudio();
    byte[] bytes;
    try { bytes = audio.getBytes(); } catch (IOException e) { throw BbsVoiceException.invalidAudio(); }
    boolean valid = switch (mime) {
      case "audio/webm" -> bytes[0] == 0x1a && bytes[1] == 0x45 && bytes[2] == (byte) 0xdf && bytes[3] == (byte) 0xa3;
      case "audio/mp4" -> ascii(bytes, 4).equals("ftyp");
      case "audio/wav" -> ascii(bytes, 0).equals("RIFF") && ascii(bytes, 8).equals("WAVE");
      default -> false;
    };
    if (!valid) throw BbsVoiceException.invalidAudio();
    if (!client.configured()) throw BbsVoiceException.notConfigured();
    try (var ignored = guard.acquire(address)) { return client.transcribe(bytes, mime); }
  }
  private static String ascii(byte[] bytes, int start) { return new String(bytes, start, 4, StandardCharsets.US_ASCII); }
}
