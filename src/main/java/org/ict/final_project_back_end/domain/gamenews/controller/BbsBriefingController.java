package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsBriefingResponse;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsBriefingException;
import org.ict.final_project_back_end.domain.gamenews.service.BbsBriefingService;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/games/bbs/{appId}/news/{sourceId}/briefing")
public class BbsBriefingController {
  private final BbsBriefingService service;
  public BbsBriefingController(BbsBriefingService service) { this.service=service; }
  public record GenerateRequest(String sourceHash) {}
  @GetMapping
  public ResponseEntity<BbsBriefingResponse> get(@PathVariable long appId, @PathVariable String sourceId, @RequestParam String sourceHash) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(appId,sourceId,sourceHash));
  }
  @PostMapping
  public ResponseEntity<BbsBriefingResponse> generate(@PathVariable long appId, @PathVariable String sourceId,
      @RequestBody GenerateRequest body, HttpServletRequest request) {
    if (body == null) throw BbsBriefingException.invalid();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.generate(appId,sourceId,body.sourceHash(),request.getRemoteAddr()));
  }
  @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,MissingServletRequestParameterException.class})
  public ResponseEntity<ErrorResponse> invalid() {
    return ResponseEntity.badRequest().body(new ErrorResponse("BBS_BRIEFING_INVALID","선택한 소식을 다시 확인해 주세요."));
  }
}
