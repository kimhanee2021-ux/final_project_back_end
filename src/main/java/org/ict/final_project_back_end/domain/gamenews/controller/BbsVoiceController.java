package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.gamenews.service.BbsTranscriptionService;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequestMapping("/api/games/bbs/voice")
public class BbsVoiceController {
  private final BbsTranscriptionService service;
  public BbsVoiceController(BbsTranscriptionService service) { this.service = service; }

  @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Transcript> transcribe(@RequestPart("audio") MultipartFile audio, HttpServletRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new Transcript(service.transcribe(audio, request.getRemoteAddr())));
  }
  public record Transcript(String transcript) {}

  @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class})
  public ResponseEntity<ErrorResponse> invalidFile() {
    return ResponseEntity.badRequest().body(new ErrorResponse("BBS_AUDIO_INVALID", "4MB 이하의 음성 파일을 보내 주세요."));
  }
}
