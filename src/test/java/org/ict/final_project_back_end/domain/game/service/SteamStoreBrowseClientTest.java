package org.ict.final_project_back_end.domain.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SteamStoreBrowseClientTest {

  // 실제 GetItems 응답(할인/정가/무료/DLC/판매 정보 없음/success 아님)을 줄여 둔 것. type 0=게임, 4=DLC
  private static final String RESPONSE = """
      {"response":{"store_items":[
        {"id":1091500,"success":1,"type":0,"appid":1091500,"best_purchase_option":{"final_price_in_cents":"1980000","original_price_in_cents":"6600000","formatted_final_price":"₩ 19,800","discount_pct":70}},
        {"id":1086940,"success":1,"type":0,"appid":1086940,"best_purchase_option":{"final_price_in_cents":"6600000","formatted_final_price":"₩ 66,000"}},
        {"id":730,"success":1,"type":0,"appid":730,"is_free":true},
        {"id":2138330,"success":1,"type":4,"appid":2138330,"best_purchase_option":{"final_price_in_cents":"2094000","formatted_final_price":"₩ 20,940","discount_pct":40}},
        {"id":111,"success":1,"type":0,"appid":111},
        {"id":222,"success":2,"type":0,"appid":222}
      ]}}
      """;

  @Test
  void 게임만_남기고_할인_정가_무료를_구분하며_가격을_알_수_없는_항목은_뺀다() throws Exception {
    Map<String, Object> body = new ObjectMapper().readValue(RESPONSE, new TypeReference<>() {});

    List<SteamStoreBrowseClient.PriceItem> items = SteamStoreBrowseClient.parse(body);

    assertThat(items).containsExactly(
        new SteamStoreBrowseClient.PriceItem(1091500, "₩ 19,800", 70),
        new SteamStoreBrowseClient.PriceItem(1086940, "₩ 66,000", 0),
        new SteamStoreBrowseClient.PriceItem(730, "무료", 0));
  }

  @Test
  void 응답이_비어_있으면_빈_목록() {
    assertThat(SteamStoreBrowseClient.parse(null)).isEmpty();
    assertThat(SteamStoreBrowseClient.parse(Map.of())).isEmpty();
  }
}
