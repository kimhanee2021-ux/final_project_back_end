package org.ict.final_project_back_end.domain.wishsales.controller;

import org.ict.final_project_back_end.domain.wishsales.dto.BuyAdviceDtos.BuyAdviceResponse;
import org.ict.final_project_back_end.domain.wishsales.service.BuyAdviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// [추가] 지금 살지 기다릴지 판단 API.
// GET /api/wishsales/buy-advice?appId=1245620
// /api/wishsales/** 는 로그인 없이 허용되어 있어서 보안 설정을 바꾸지 않는다.
@RestController
@RequestMapping("/api/wishsales")
public class BuyAdviceController {
    private final BuyAdviceService service;

    public BuyAdviceController(BuyAdviceService service) {
        this.service = service;
    }

    @GetMapping("/buy-advice")
    public ResponseEntity<BuyAdviceResponse> advise(@RequestParam("appId") long appId) {
        return ResponseEntity.ok(service.advise(appId));
    }
}
