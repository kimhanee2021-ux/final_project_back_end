package org.ict.final_project_back_end.domain.aiPc.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 카탈로그에 등록된 판매처 상품 페이지 HTML을 가져온다. 가격 해석은 PcPartPrices/PcRetailPrices가 한다. */
@Component
public class PcPriceSourceClient {
  private static final int MAX_BODY_BYTES = 2097152;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

  // 200이 아니거나 2MB를 넘는 응답은 IllegalStateException으로 실패시킨다.
  public String fetchPage(String url) throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("User-Agent", "GameSense-PC-Research/1.0")
            .GET()
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = response.body()) {
      byte[] body = stream.readNBytes(MAX_BODY_BYTES + 1);
      if (response.statusCode() != 200 || body.length > MAX_BODY_BYTES)
        throw new IllegalStateException();
      return new String(body, StandardCharsets.UTF_8);
    }
  }
}
