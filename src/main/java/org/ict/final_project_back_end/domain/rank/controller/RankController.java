package org.ict.final_project_back_end.domain.rank.controller;

import org.ict.final_project_back_end.domain.rank.dto.WeeklyRankResponse;
import org.ict.final_project_back_end.domain.rank.service.RankQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ranks")
public class RankController {

  private final RankQueryService service;

  public RankController(RankQueryService service) {
    this.service = service;
  }

  // week는 week_start와 정확히 일치하는 날짜. 기본 주/다른 국가로 자동 대체하지 않는다.
  @GetMapping("/weekly")
  public ResponseEntity<WeeklyRankResponse> weekly(
      @RequestParam(name = "country") String country,
      @RequestParam(name = "week") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
    return ResponseEntity.ok(service.getWeeklyRank(country, week));
  }
}
