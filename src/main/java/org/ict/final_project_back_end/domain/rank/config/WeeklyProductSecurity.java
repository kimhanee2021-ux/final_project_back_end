package org.ict.final_project_back_end.domain.rank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Only the two read-only public archive endpoints use this chain. */
@Configuration
public class WeeklyProductSecurity {
  @Bean
  @Order(1)
  SecurityFilterChain weeklyProductReadOnlyChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/ranks/weekly/products", "/api/ranks/weekly/options")
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/ranks/weekly/products", "/api/ranks/weekly/options").permitAll()
            .anyRequest().denyAll())
        .build();
  }
}
