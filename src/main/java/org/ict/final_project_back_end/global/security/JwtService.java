package org.ict.final_project_back_end.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// JWT를 발급하고(로그인 성공 시) 검증하는(매 요청마다) 역할을 모두 담당한다.
@Component
public class JwtService {

  // jwt.secret-key 문자열을 바이트로 변환해 만든 서명용 대칭키.
  // HS256 알고리즘은 최소 256비트(32바이트) 이상의 키를 요구하므로, JWT_SECRET_KEY가 너무 짧으면
  // 이 생성자에서 바로 예외가 발생해 앱 시작 자체가 실패한다.
  private final SecretKey key;
  private final long expirationMs;

  public JwtService(@Value("${jwt.secret-key}") String secret,
                    @Value("${jwt.expiration-ms}") long expirationMs) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
  }

  // 로그인 성공 시 호출. 유저 id를 subject로, 이메일을 커스텀 claim으로 담아 서명된 토큰을 만든다.
  public String generateToken(Long userId, String email) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  // 요청 헤더로 들어온 토큰을 검증하고 유저 id를 꺼낸다.
  // 서명이 안 맞거나 만료됐으면 여기서 예외가 던져지고, 호출부(JwtAuthFilter)가 이를 잡아 처리한다.
  public Long parseUserId(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return Long.valueOf(claims.getSubject());
  }

  // 액세스 토큰과 별개로, 리다이렉트 왕복(ex. 소셜 계정 연동) 동안만 "누가 요청했는지"를 짧게
  // 증명하면 되는 흐름에서 쓰는 단기 상태 토큰. 액세스 토큰을 그대로 URL에 노출하지 않기 위해
  // 발급 시 용도(purpose)와 만료시간을 직접 지정하고, 검증 시 purpose가 다르면 거부한다.
  public String generateStateToken(Long userId, String purpose, long expirationMs) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("purpose", purpose)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  public Long parseStateToken(String token, String purpose) {
    Claims claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    if (!purpose.equals(claims.get("purpose", String.class))) {
      throw new IllegalArgumentException("토큰 용도가 일치하지 않습니다");
    }
    return Long.valueOf(claims.getSubject());
  }
}
