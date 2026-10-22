package org.ict.final_project_back_end.domain.wishsales.controller;

import org.ict.final_project_back_end.domain.wishsales.dto.DiscountOutlookDtos.DiscountOutlookResponse;
import org.ict.final_project_back_end.domain.wishsales.service.DiscountOutlookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// [추가] 할인 달 전망 API.
// GET /api/games/discount-outlook?appId=1245620&threshold=30
// /api/games/** 는 로그인 없이 허용되어 있어서 보안 설정을 바꾸지 않는다.
@RestController
@RequestMapping("/api/games")
public class DiscountOutlookController {
    private final DiscountOutlookService service;

    public DiscountOutlookController(DiscountOutlookService service) {
        this.service = service;
    }

    @GetMapping("/discount-outlook")
    public ResponseEntity<DiscountOutlookResponse> outlook(
            @RequestParam("appId") long appId,
            @RequestParam(value = "threshold", defaultValue = "30") int threshold) {
        return ResponseEntity.ok(service.outlook(appId, threshold));
    }
}
