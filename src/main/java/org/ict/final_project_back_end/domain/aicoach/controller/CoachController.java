package org.ict.final_project_back_end.domain.aicoach.controller;

import jakarta.validation.Valid;
import org.ict.final_project_back_end.domain.aicoach.controller.docs.CoachApiDocs;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatResponse;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachInsightOverview;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachInsightResponse;
import org.ict.final_project_back_end.domain.aicoach.service.CoachInsightService;
import org.ict.final_project_back_end.domain.aicoach.service.CoachService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI 게임 소비 코치 API. 실제 로직은 CoachService·CoachInsightService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/ai/coach")
public class CoachController implements CoachApiDocs {

  private final CoachService coachService;
  private final CoachInsightService insightService;

  public CoachController(CoachService coachService, CoachInsightService insightService) {
    this.coachService = coachService;
    this.insightService = insightService;
  }

  @Override
  @PostMapping("/chat")
  public ResponseEntity<CoachChatResponse> chat(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody CoachChatRequest request) {
    return ResponseEntity.ok(coachService.chat(userId, request));
  }

  @Override
  @GetMapping("/insight/overview")
  public ResponseEntity<CoachInsightOverview> insightOverview(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(insightService.overview(userId));
  }

  @Override
  @GetMapping("/insight")
  public ResponseEntity<CoachInsightResponse> insight(@AuthenticationPrincipal Long userId,
                                                      @RequestParam String topic) {
    return ResponseEntity.ok(insightService.insight(userId, topic));
  }
}
