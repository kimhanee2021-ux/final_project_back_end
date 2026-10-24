package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.game.service.GameIdentityValidator;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.ict.final_project_back_end.domain.game.service.GameVoiceLexicon;
import org.ict.final_project_back_end.domain.gamenews.client.transcription.NewsTranscriptionClient;
import org.ict.final_project_back_end.domain.gamenews.service.NewsVoiceAiLimits;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/api/games/news/transcribe")
public class GameNewsTranscriptionController {
  public record Response(String transcript) {}

  private final NewsTranscriptionClient client;
  private final GameIdentityValidator identities;
  private final GameVoiceLexicon lexicon;
  private final NewsVoiceAiLimits limits;

  public GameNewsTranscriptionController(
      NewsTranscriptionClient client,
      GameIdentityValidator identities,
      GameVoiceLexicon lexicon,
      NewsVoiceAiLimits limits) {
    this.client = client;
    this.identities = identities;
    this.lexicon = lexicon;
    this.limits = limits;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Response> transcribe(
      @RequestParam("audio") MultipartFile audio,
      @RequestParam(required = false) String appId,
      HttpServletRequest request)
      throws IOException {
    if (audio.isEmpty() || audio.getSize() > 4_000_000)
      throw GameSearchException.invalid("음성은 4MB 이하로 보내 주세요.");
    String mime =
        audio.getContentType() == null ? "" : audio.getContentType().split(";")[0].strip();
    if (!Set.of("audio/webm", "audio/ogg", "audio/mp4", "audio/wav").contains(mime))
      throw GameSearchException.invalid("지원하지 않는 음성 형식입니다.");
    byte[] bytes = audio.getBytes();
    if (!signature(bytes, mime)) throw GameSearchException.invalid("올바른 음성 파일이 아닙니다.");
    String name = appId == null ? null : identities.require(appId).getName();
    String remote = request.getRemoteAddr();
    try (var permit = limits.acquire("stt:" + remote, remote)) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(new Response(client.transcribe(bytes, mime, lexicon.hints(name))));
    }
  }

  private boolean signature(byte[] b, String mime) {
    if (b.length < 12) return false;
    String first = new String(b, 0, 4, StandardCharsets.US_ASCII);
    return switch (mime) {
      case "audio/wav" ->
          first.equals("RIFF") && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
      case "audio/ogg" -> first.equals("OggS");
      case "audio/mp4" -> new String(b, 4, 4, StandardCharsets.US_ASCII).equals("ftyp");
      default -> b[0] == 0x1a && b[1] == 0x45 && (b[2] & 255) == 0xdf && (b[3] & 255) == 0xa3;
    };
  }
}
