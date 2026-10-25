package org.ict.final_project_back_end.domain.gamenews.controller;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsAudioException;
import org.ict.final_project_back_end.domain.gamenews.service.BbsAudioService;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/games/bbs/{appId}/news/{sourceId}/briefing/audio")
public class BbsAudioController {
  private final BbsAudioService service;
  public BbsAudioController(BbsAudioService service) { this.service=service; }
  public record AudioRequest(String sourceHash,Instant generatedAt) {}
  @PostMapping
  public ResponseEntity<byte[]> generate(@PathVariable long appId,@PathVariable String sourceId,
      @RequestBody AudioRequest body,HttpServletRequest request) {
    if (body==null) throw BbsAudioException.invalid();
    var audio=service.generate(appId,sourceId,body.sourceHash(),body.generatedAt(),request.getRemoteAddr());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("audio/wav"))
        .header("X-Content-Type-Options","nosniff").contentLength(audio.wav().length).body(audio.wav());
  }
  @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class})
  public ResponseEntity<ErrorResponse> invalid() {
    return ResponseEntity.badRequest().body(new ErrorResponse("BBS_AUDIO_INVALID","선택한 소식의 요약을 다시 확인해 주세요."));
  }
}
