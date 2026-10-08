package org.ict.final_project_back_end.global.constant;

public final class SteamUrls {

  // 스팀 커뮤니티 베이스 URL. OpenID 엔드포인트 등 스팀 외부 호출의 기준이 된다.
  public static final String STEAM_COMMUNITY_BASE_URL = "https://steamcommunity.com";
  // 스팀 OpenID 공급자 엔드포인트. 로그인 요청(checkid_setup)과 서명 검증(check_authentication)
  // 둘 다 이 주소로 보낸다.
  public static final String STEAM_OPENID_ENDPOINT = STEAM_COMMUNITY_BASE_URL + "/openid/login";
  // 스팀 Web API 베이스 URL. 보유 게임 목록 조회(IPlayerService) 등에 사용한다.
  public static final String STEAM_API_BASE_URL = "https://api.steampowered.com";
  // 스팀 스토어 API 베이스 URL. 앱 상세/가격 조회(appdetails) 등에 사용한다.
  public static final String STEAM_STORE_BASE_URL = "https://store.steampowered.com";

  private SteamUrls() {
  }
}
