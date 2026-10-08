package org.ict.final_project_back_end.domain.library.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 스팀 스토어 appdetails API 응답 원본 구조 (cc=kr, filters=price_overview 기준).
// 최상위 키가 appId 문자열이라 Map<String, SteamAppDetailsResponse>로 역직렬화한다.
// 비공개/삭제된 앱이면 success가 false이고 data가 없다.
public record SteamAppDetailsResponse(
    Boolean success,
    Data data
) {

  public record Data(
      @JsonProperty("is_free") Boolean isFree,
      @JsonProperty("price_overview") PriceOverview priceOverview
  ) {
  }

  // initial/final은 실제 금액의 100배 값으로 내려온다 (예: 10000 = ₩100)
  public record PriceOverview(
      String currency,
      Integer initial,
      @JsonProperty("final") Integer finalPrice,
      @JsonProperty("discount_percent") Integer discountPercent
  ) {
  }
}
