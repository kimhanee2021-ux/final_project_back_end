package org.ict.final_project_back_end.domain.wishsales.controller;

import org.ict.final_project_back_end.domain.wishsales.dto.AiSummaryDtos.AiSummaryResponse;
import org.ict.final_project_back_end.domain.wishsales.service.AiSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// [추가] AI 요약 API. 위시리스트의 "AI 요약 보기" 버튼을 누를 때 한 번만 호출된다.
// GET /api/games/ai-summary?appIds=1245620,1091500
// /api/games/** 는 로그인 없이 허용되어 있어서 보안 설정을 바꾸지 않는다.
@RestController
@RequestMapping("/api/games")
public class AiSummaryController {
    private final AiSummaryService service;

    public AiSummaryController(AiSummaryService service) {
        this.service = service;
    }

    @GetMapping("/ai-summary")
    public ResponseEntity<AiSummaryResponse> summary(@RequestParam("appIds") String appIds) {
        // "1245620,1091500" 문자열을 숫자 목록으로 바꾼다. 공백과 빈 값은 무시한다.
        List<Long> ids = Arrays.stream(appIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        return ResponseEntity.ok(service.summarize(ids));
    }
}
