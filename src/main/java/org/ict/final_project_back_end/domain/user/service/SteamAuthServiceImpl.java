package org.ict.final_project_back_end.domain.user.service;

import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.constant.SteamUrls;
import org.ict.final_project_back_end.global.exception.DuplicateSteamIdException;
import org.ict.final_project_back_end.global.exception.InvalidCredentialsException;
import org.ict.final_project_back_end.global.exception.InvalidSteamAuthException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.ict.final_project_back_end.global.security.JwtService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_OPENID_ENDPOINT;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SteamAuthServiceImpl implements SteamAuthService {

  // claimed_id는 "https://steamcommunity.com/openid/id/아이디" 형태로 오고,
  // 끝의 17자리 숫자가 바로 우리가 필요한 SteamID64다.
  private static final Pattern STEAM_ID_PATTERN =
      Pattern.compile("^" + Pattern.quote(SteamUrls.STEAM_COMMUNITY_BASE_URL) + "/openid/id/(\\d{17})$");
  // 연동 흐름(스팀 로그인 페이지 -> 콜백) 왕복에만 쓰는 상태 토큰의 용도 식별자/유효시간.
  private static final String STEAM_LINK_PURPOSE = "steam-link";
  private static final long STEAM_LINK_STATE_EXPIRATION_MS = 5 * 60 * 1000L;

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final RestClient restClient = RestClient.create();

  public SteamAuthServiceImpl(UserRepository userRepository, JwtService jwtService) {
    this.userRepository = userRepository;
    this.jwtService = jwtService;
  }

  @Override
  public URI buildLoginRedirectUri(String accessToken, String callbackUrl, String realmUrl) {
    Long userId;
    try {
      userId = jwtService.parseUserId(accessToken);
    } catch (Exception e) {
      throw new InvalidCredentialsException("유효하지 않거나 만료된 토큰입니다");
    }
    String state = jwtService.generateStateToken(userId, STEAM_LINK_PURPOSE, STEAM_LINK_STATE_EXPIRATION_MS);

    String returnTo = UriComponentsBuilder.fromUriString(callbackUrl)
        .queryParam("state", state)
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();

    return UriComponentsBuilder.fromUriString(STEAM_OPENID_ENDPOINT)
        .queryParam("openid.ns", "http://specs.openid.net/auth/2.0")
        .queryParam("openid.mode", "checkid_setup")
        .queryParam("openid.return_to", returnTo)
        .queryParam("openid.realm", realmUrl)
        .queryParam("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
        .queryParam("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select")
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUri();
  }

  @Override
  @Transactional
  public void linkSteamAccount(String state, Map<String, String> openIdParams) {
    Long userId;
    try {
      userId = jwtService.parseStateToken(state, STEAM_LINK_PURPOSE);
    } catch (Exception e) {
      throw new InvalidSteamAuthException("연동 요청이 만료되었거나 올바르지 않습니다. 다시 시도해주세요");
    }

    if (!"id_res".equals(openIdParams.get("openid.mode"))) {
      throw new InvalidSteamAuthException("스팀 로그인이 취소되었거나 실패했습니다");
    }

    String steamId = extractSteamId(openIdParams.get("openid.claimed_id"));

    // 스팀이 보낸 파라미터는 중간에 조작되었을 수 있으므로, 그대로 다시 스팀에 보내
    // 서명이 유효한지(check_authentication) 재검증받기 전까지는 신뢰하지 않는다.
    verifyWithSteam(openIdParams);

    userRepository.findBySteamId(steamId).ifPresent(existing -> {
      if (!existing.getId().equals(userId)) {
        throw new DuplicateSteamIdException();
      }
    });

    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    user.setSteamId(steamId);
    userRepository.save(user);
  }

  private String extractSteamId(String claimedId) {
    if (claimedId == null) {
      throw new InvalidSteamAuthException("스팀 계정 식별에 실패했습니다");
    }
    Matcher matcher = STEAM_ID_PATTERN.matcher(claimedId);
    if (!matcher.matches()) {
      throw new InvalidSteamAuthException("스팀 계정 식별에 실패했습니다");
    }
    return matcher.group(1);
  }

  private void verifyWithSteam(Map<String, String> openIdParams) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    openIdParams.forEach(body::add);
    body.set("openid.mode", "check_authentication");

    String response = restClient.post()
        .uri(STEAM_OPENID_ENDPOINT)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .body(String.class);

    if (response == null || !response.contains("is_valid:true")) {
      throw new InvalidSteamAuthException("스팀 인증 검증에 실패했습니다");
    }
  }
}
