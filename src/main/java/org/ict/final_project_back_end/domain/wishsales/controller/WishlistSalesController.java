package org.ict.final_project_back_end.domain.wishsales.controller;

import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.WishlistSalesResponse;
import org.ict.final_project_back_end.domain.wishsales.service.WishlistSalesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// [추가] 위시리스트 세일 요약 API.
// GET /api/wishsales/wishlist-sales?appIds=730,570
// /api/wishsales/** 는 SecurityConfig에서 로그인 없이 허용되어 있어서 보안 설정을 바꿀 필요가 없다.
// GET으로 만든 이유: 조회만 하므로 POST보다 단순하고, CSRF 설정 영향을 받지 않는다.
@RestController
@RequestMapping("/api/wishsales")
public class WishlistSalesController {
    private final WishlistSalesService service;

    public WishlistSalesController(WishlistSalesService service) {
        this.service = service;
    }

    @GetMapping("/wishlist-sales")
    public ResponseEntity<WishlistSalesResponse> sales(@RequestParam("appIds") String appIds) {
        // "730,570" 문자열을 잘라서 Long 목록으로 바꾼다.
        // 공백과 빈 값은 무시하고, 숫자가 아니면 NumberFormatException이 난다 (잘못된 요청)
        List<Long> ids = Arrays.stream(appIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        return ResponseEntity.ok(service.build(ids));
    }
}
