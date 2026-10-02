package org.ict.final_project_back_end.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 요청마다 한 번씩 실행되며(OncePerRequestFilter), Authorization 헤더의 JWT를 읽어서
// 유효하면 SecurityContext에 "로그인된 상태"를 심어주는 필터.
// 세션을 쓰지 않으므로 이 필터가 매 요청마다 로그인 여부를 다시 확인하는 역할을 한다.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    // "Bearer <토큰>" 형식이 아니면 그냥 인증 없이 다음 필터로 통과시킨다 (비로그인 요청일 수도 있으므로)
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        Long userId = jwtService.parseUserId(token);
        // 비밀번호 없이 userId만으로 인증 객체를 만들어 SecurityContext에 등록.
        // 이후 컨트롤러에서 @AuthenticationPrincipal Long userId 로 꺼내 쓸 수 있다.
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception ignored) {
        // 유효하지 않은/만료된 토큰이면 인증 없이 진행 -> 이후 authorizeHttpRequests에서 401 처리
      }
    }
    filterChain.doFilter(request, response);
  }
}
