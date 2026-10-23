package org.ict.final_project_back_end.domain.user.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.ict.final_project_back_end.domain.user.service.SteamAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

// 로그인된 사용자의 계정에 스팀 고유 ID(17자리)를 연동하는 API.
// 스팀은 OAuth2가 아니라 OpenID 2.0 리다이렉트 기반 로그인만 제공하므로, 두 요청 모두
// JSON 응답이 아니라 브라우저를 다른 곳으로 이동시키는 302 리다이렉트로 동작한다.
@RestController
@RequestMapping("/api/auth/steam")
@Hidden // 리다이렉트 기반 흐름이라 스웨거 "Try it out"으로 테스트할 수 없어 문서에서 숨긴다
public class SteamAuthController {

  private final SteamAuthService steamAuthService;

  @Value("${app.frontend-base-url}")
  private String frontendBaseUrl;

  public SteamAuthController(SteamAuthService steamAuthService) {
    this.steamAuthService = steamAuthService;
  }

  // 프론트에서 로그인된 사용자가 "스팀 연동" 버튼을 누르면 전체 페이지를 이 URL로 이동시킨다.
  // Authorization 헤더가 아니라 쿼리 파라미터로 토큰을 받는 이유: 스팀 로그인 페이지로 가는 302
  // 리다이렉트는 브라우저의 최상위 탐색(top-level navigation)이라 커스텀 헤더를 실어 보낼 수 없다.
  @GetMapping
  // popup=true는 프론트가 연동을 새 창(팝업)으로 띄웠다는 표시다. 스팀을 다녀온 콜백까지 이 값을 들고 와서,
  // 끝난 뒤 프론트가 "이 창은 팝업이니 원래 창에 알리고 닫는다"고 판단할 수 있게 한다.
  public ResponseEntity<Void> steamLogin(@RequestParam String token,
                                         @RequestParam(required = false) boolean popup,
                                         HttpServletRequest request) {
    String baseUrl = baseUrl(request);
    URI redirectUri = steamAuthService.buildLoginRedirectUri(
        token,
        baseUrl + "/api/auth/steam/callback" + (popup ? "?popup=true" : ""),
        baseUrl + "/");
    return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
  }

  // 스팀이 로그인 처리 후 돌려보내는 콜백. 서명 검증 후 state에 담겨 있던 계정에 steamId를
  // 저장하고 프론트엔드로 다시 리다이렉트한다.
  @GetMapping("/callback")
  public ResponseEntity<Void> steamCallback(@RequestParam String state,
                                             @RequestParam(required = false) boolean popup,
                                             @RequestParam Map<String, String> allParams) {
    steamAuthService.linkSteamAccount(state, allParams);
    UriComponentsBuilder redirect = UriComponentsBuilder.fromUriString(frontendBaseUrl)
        .queryParam("steamLinked", "true");
    if (popup) {
      redirect.queryParam("popup", "true");
    }
    URI redirectUri = redirect.build().toUri();
    return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
  }

  private String baseUrl(HttpServletRequest request) {
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
        || ("https".equals(request.getScheme()) && port == 443);
    return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
  }
}
