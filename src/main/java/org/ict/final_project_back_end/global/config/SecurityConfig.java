package org.ict.final_project_back_end.global.config;

import org.ict.final_project_back_end.global.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// 스프링 시큐리티 전체 설정 - 어떤 요청을 막고 열지 여기서 결정한다.
// 세션 기반이 아니라 JWT 기반 인증이라, 로그인 성공 후에는 세션 쿠키 대신
// JwtAuthFilter가 매 요청마다 헤더의 JWT를 검사하는 방식으로 동작한다.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${app.frontend-base-url}")
  private String frontendBaseUrl;

  // 비밀번호 해시에 사용할 인코더. 회원가입/로그인 시 평문 비밀번호를 절대 그대로 저장/비교하지 않는다.
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
    http
            // JWT는 쿠키가 아니라 헤더로 전달되므로 CSRF 공격 대상이 아님 -> 비활성화
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            // JWT만으로 인증하므로 서버 세션을 만들지 않는다
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // 로그인/회원가입, 스웨거는 인증 없이 허용
                    .requestMatchers("/api/auth/**","/api/games/**", "/api/wishsales/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    // 그 외 /api/** 는 반드시 로그인(JWT)이 있어야 접근 가능
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
            // 토큰이 없거나 잘못된 요청은 기본값(403) 대신 401로 응답 -> 프론트에서 로그인 페이지로 보내기 쉬움
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // 이메일 로그인용 필터보다 먼저 JWT 검사 필터를 태워서, 헤더에 유효한 토큰이 있으면
            // 이 시점에 SecurityContext에 인증 정보를 채워둔다
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  // 프론트엔드 origin에서 오는 요청만 허용하는 CORS 설정.
  // app.frontend-base-url과 실제 프론트 실행 주소가 다르면 브라우저가 요청을 막아버린다.
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontendBaseUrl));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}