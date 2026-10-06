package org.ict.final_project_back_end.global.constant;

public final class SteamUrls {

  // 스팀 커뮤니티 베이스 URL. OpenID 엔드포인트 등 스팀 외부 호출의 기준이 된다.
  public static final String STEAM_COMMUNITY_BASE_URL = "https://steamcommunity.com";
  // 스팀 OpenID 공급자 엔드포인트. 로그인 요청(checkid_setup)과 서명 검증(check_authentication)
  // 둘 다 이 주소로 보낸다.
  public static final String STEAM_OPENID_ENDPOINT = STEAM_COMMUNITY_BASE_URL + "/openid/login";
  // 스팀 Web API 베이스 URL. 보유 게임 목록 조회(IPlayerService) 등에 사용한다.
  public static final String STEAM_API_BASE_URL = "https://api.steampowered.com";
  // 스팀 전체 앱 목록 조회. 예전의 ISteamApps/GetAppList/v2는 현재 404이므로 IStoreService를 쓴다 (API 키 필요).
  public static final String STEAM_APP_LIST_URL = STEAM_API_BASE_URL + "/IStoreService/GetAppList/v1/";
  // 스팀 스토어 상세정보 조회(appdetails). API 키 없이 호출된다.
  public static final String STEAM_STORE_BASE_URL = "https://store.steampowered.com";

  private SteamUrls() {
  }
}
