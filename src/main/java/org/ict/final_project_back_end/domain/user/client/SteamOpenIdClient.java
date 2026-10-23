package org.ict.final_project_back_end.domain.user.client;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_OPENID_ENDPOINT;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// 스팀 OpenID 서버에 콜백 파라미터를 그대로 다시 보내(check_authentication) 서명이 유효한지 확인한다.
@Component
public class SteamOpenIdClient {

  private final RestClient restClient = RestClient.create();

  public boolean isValid(Map<String, String> openIdParams) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    openIdParams.forEach(body::add);
    body.set("openid.mode", "check_authentication");

    String response = restClient.post()
        .uri(STEAM_OPENID_ENDPOINT)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .body(String.class);
    return response != null && response.contains("is_valid:true");
  }
}
