package org.ict.final_project_back_end.domain.aiCoach.controller;

import jakarta.validation.Valid;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.domain.aiCoach.service.CoachService;
import org.ict.final_project_back_end.global.swagger.CoachApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI 게임 소비 코치 API. 실제 로직은 CoachService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/ai/coach")
public class CoachController implements CoachApiDocs {

  private final CoachService coachService;

  public CoachController(CoachService coachService) {
    this.coachService = coachService;
  }

  @Override
  @PostMapping("/chat")
  public ResponseEntity<CoachChatResponse> chat(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody CoachChatRequest request) {
    return ResponseEntity.ok(coachService.chat(userId, request));
  }
}
