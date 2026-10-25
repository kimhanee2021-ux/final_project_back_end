package org.ict.final_project_back_end.domain.library.service;

import org.ict.final_project_back_end.domain.library.client.SteamLibraryClient;
import org.ict.final_project_back_end.domain.library.dto.LibraryGameResponse;
import org.ict.final_project_back_end.domain.library.dto.SteamOwnedGamesResponse;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.exception.SteamNotLinkedException;
import org.ict.final_project_back_end.domain.user.exception.UserNotFoundException;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LibraryServiceImpl implements LibraryService {

  // 스팀 게임 아이콘 경로 형식: .../apps/{appid}/{img_icon_url}.jpg
  private static final String STEAM_ICON_URL_FORMAT = "https://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final SteamLibraryClient steamLibraryClient;

  public LibraryServiceImpl(UserRepository userRepository,
                            LibraryGameRepository libraryGameRepository,
                            SteamLibraryClient steamLibraryClient) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.steamLibraryClient = steamLibraryClient;
  }

  @Override
  @Transactional
  public List<LibraryGameResponse> syncSteamLibrary(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.getSteamId() == null) {
      throw new SteamNotLinkedException();
    }

    List<SteamOwnedGamesResponse.Game> steamGames = steamLibraryClient.fetchOwnedGames(user.getSteamId());

    // appId 기준으로 기존 행을 찾아 갱신하고, 없는 게임만 새로 만든다 (중복 저장 방지)
    Map<Long, LibraryGame> existingByAppId = libraryGameRepository.findAllByUserId(userId).stream()
        .collect(Collectors.toMap(LibraryGame::getAppId, Function.identity()));

    List<LibraryGame> toSave = steamGames.stream()
        .map(steamGame -> upsert(user, existingByAppId.get(steamGame.appid()), steamGame))
        .toList();
    libraryGameRepository.saveAll(toSave);

    return libraryGameRepository.findAllByUserIdOrderByNameAsc(userId).stream()
        .map(LibraryGameResponse::from)
        .toList();
  }

  private LibraryGame upsert(User user, LibraryGame existing, SteamOwnedGamesResponse.Game steamGame) {
    String iconUrl = steamGame.imgIconUrl() == null
        ? null
        : STEAM_ICON_URL_FORMAT.formatted(steamGame.appid(), steamGame.imgIconUrl());
    // 스팀은 한 번도 플레이하지 않은 게임의 마지막 플레이 시각을 0으로 내려준다
    LocalDateTime lastPlayedAt = steamGame.rtimeLastPlayed() == null || steamGame.rtimeLastPlayed() == 0
        ? null
        : LocalDateTime.ofInstant(Instant.ofEpochSecond(steamGame.rtimeLastPlayed()), ZoneId.systemDefault());
    Integer playtimeForever = valueOrZero(steamGame.playtimeForever());
    Integer playtime2Weeks = valueOrZero(steamGame.playtime2Weeks());

    if (existing != null) {
      existing.updateFromSteam(steamGame.name(), playtimeForever, playtime2Weeks, lastPlayedAt, iconUrl);
      return existing;
    }
    return LibraryGame.builder()
        .user(user)
        .appId(steamGame.appid())
        .name(steamGame.name())
        .playtimeForever(playtimeForever)
        .playtime2Weeks(playtime2Weeks)
        .lastPlayedAt(lastPlayedAt)
        .iconUrl(iconUrl)
        .build();
  }

  private Integer valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }
}
