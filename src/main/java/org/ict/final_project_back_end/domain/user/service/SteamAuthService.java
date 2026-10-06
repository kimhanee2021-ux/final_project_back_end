package org.ict.final_project_back_end.domain.user.service;

import java.net.URI;
import java.util.Map;

// 로그인된 사용자의 계정에 스팀 고유 ID(SteamID64, 17자리)를 연동하는 서비스.
// 스팀은 OAuth2가 아니라 OpenID 2.0 기반 로그인만 제공하므로 이메일 인증(AuthService)과는
// 별도의 흐름으로 분리했다.
public interface SteamAuthService {

  // 연동을 시작하는 사용자를 식별할 액세스 토큰을 검증하고, 스팀 로그인 페이지로 보낼 URL을 만든다.
  URI buildLoginRedirectUri(String accessToken, String callbackUrl, String realmUrl);

  // 스팀 콜백 파라미터를 검증하고, state에 담겨 있던 사용자 계정에 steamId를 저장한다.
  void linkSteamAccount(String state, Map<String, String> openIdParams);
}
