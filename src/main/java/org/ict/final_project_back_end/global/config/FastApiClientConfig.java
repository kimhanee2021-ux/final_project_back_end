package org.ict.final_project_back_end.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// FastAPI 모델 서버(추천 · 검색)를 부르는 HTTP 클라이언트. 인증이 없는 내부 서버라 스프링만 부른다.
// 추천과 검색이 같은 서버라 주소·제한 시간 설정(app.recommend.*)을 같이 쓴다.
@Configuration
public class FastApiClientConfig {

  public static final String FAST_API_REST_CLIENT = "fastApiRestClient";

  @Bean(FAST_API_REST_CLIENT)
  public RestClient fastApiRestClient(@Value("${app.recommend.base-url:http://localhost:8000}") String baseUrl,
                                      @Value("${app.recommend.timeout-seconds:30}") long timeoutSeconds) {
    // JDK 기본값(HTTP/2)은 http://에서 h2c 업그레이드를 시도하는데, uvicorn이 이를 거부해 본문 없는 요청(400)이 된다
    HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
  }
}
