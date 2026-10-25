package org.ict.final_project_back_end.domain.gamenews.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsCommandException;
import org.ict.final_project_back_end.domain.gamenews.service.BbsCommandService;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/bbs/voice/commands")
public class BbsCommandController {
  private final BbsCommandService service;
  public BbsCommandController(BbsCommandService service) { this.service=service; }
  public record CommandRequest(String transcript) {}
  @PostMapping
  public ResponseEntity<BbsCommandService.Result> resolve(@RequestBody CommandRequest body,HttpServletRequest request) {
    if(body==null) throw BbsCommandException.invalid();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resolve(body.transcript(),request.getRemoteAddr()));
  }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> invalid() { return ResponseEntity.badRequest().body(new ErrorResponse("BBS_COMMAND_INVALID","게임 이름과 요청을 짧게 말씀해 주세요.")); }
}
