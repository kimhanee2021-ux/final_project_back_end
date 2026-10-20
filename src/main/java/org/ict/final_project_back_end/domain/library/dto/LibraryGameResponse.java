package org.ict.final_project_back_end.domain.library.dto;

import org.ict.final_project_back_end.domain.library.entity.LibraryGame;

import java.time.LocalDateTime;

public record LibraryGameResponse(
    Long appId,
    String name,
    Integer playtimeForever,
    Integer playtime2Weeks,
    LocalDateTime lastPlayedAt,
    String iconUrl
) {
  public static LibraryGameResponse from(LibraryGame game) {
    return new LibraryGameResponse(
        game.getAppId(),
        game.getName(),
        game.getPlaytimeForever(),
        game.getPlaytime2Weeks(),
        game.getLastPlayedAt(),
        game.getIconUrl());
  }
}
