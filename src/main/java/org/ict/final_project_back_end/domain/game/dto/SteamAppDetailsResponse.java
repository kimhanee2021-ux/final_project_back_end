package org.ict.final_project_back_end.domain.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 스팀 스토어 appdetails 응답 한 건. 실제 응답은 {"appId": {"success": ..., "data": {...}}} 형태라
// 호출하는 쪽에서 Map<String, SteamAppDetailsResponse>로 받는다. success가 false이면 data가 없다.
public record SteamAppDetailsResponse(Boolean success, Data data) {

  public record Data(
      String type,
      String name,
      @JsonProperty("is_free") Boolean isFree,
      @JsonProperty("short_description") String shortDescription,
      @JsonProperty("header_image") String headerImage,
      List<String> developers,
      List<String> publishers,
      List<Genre> genres,
      @JsonProperty("price_overview") PriceOverview priceOverview,
      @JsonProperty("release_date") ReleaseDate releaseDate
  ) {
  }

  public record Genre(String description) {
  }

  // 무료 게임이거나 가격 정보가 없으면 price_overview 자체가 없다
  public record PriceOverview(
      String currency,
      @JsonProperty("initial") Integer initialPrice,
      @JsonProperty("final") Integer finalPrice,
      @JsonProperty("discount_percent") Integer discountPercent
  ) {
  }

  public record ReleaseDate(
      @JsonProperty("coming_soon") Boolean comingSoon,
      String date
  ) {
  }
}
