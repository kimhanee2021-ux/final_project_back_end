package org.ict.final_project_back_end.domain.wishsales.controller;

import org.ict.final_project_back_end.domain.wishsales.dto.AppIdResolveDtos.ResolveResponse;
import org.ict.final_project_back_end.domain.wishsales.service.SteamAppIdResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// [추가] 게임 이름으로 스팀 appid 찾기 API.
// GET /api/wishsales/resolve-appid?name=ELDEN%20RING
// /api/wishsales/** 는 로그인 없이 허용되어 있어서 보안 설정을 바꾸지 않는다.
@RestController
@RequestMapping("/api/wishsales")
public class SteamAppIdController {
    private final SteamAppIdResolver resolver;

    public SteamAppIdController(SteamAppIdResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/resolve-appid")
    public ResponseEntity<ResolveResponse> resolve(@RequestParam("name") String name) {
        return ResponseEntity.ok(resolver.resolve(name));
    }
}
