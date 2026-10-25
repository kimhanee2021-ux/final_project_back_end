package org.ict.final_project_back_end.domain.briefing.controller;

import org.ict.final_project_back_end.domain.briefing.service.BriefingContent;
import org.ict.final_project_back_end.domain.briefing.service.BriefingQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 저장된 AI 게임 브리핑 조회 API. 요청 값 검증만 하고 조회·카드 조립은 BriefingQueryService가 맡는다.
@RestController
@RequestMapping("/api/board")
public class BriefingController {

  private final BriefingQueryService briefings;

  public BriefingController(BriefingQueryService briefings) {
    this.briefings = briefings;
  }

  @GetMapping("/briefing")
  public BriefingQueryService.BriefingPage briefing(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(required = false) Long appId,
                                                    @RequestParam(required = false) String group,
                                                    @RequestParam(required = false) String topic) {
    if (page < 0 || page > 10000 || (appId != null && appId <= 0)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 페이지와 게임 ID를 입력하세요.");
    }
    if (group != null && !BriefingQueryService.GROUPS.contains(group)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 분류입니다.");
    }
    if (topic != null && !BriefingContent.TOPICS.containsKey(topic)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 소주제입니다.");
    }
    return briefings.find(page, appId, group, topic);
  }
}
