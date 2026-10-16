package org.ict.final_project_back_end.domain.review.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class ReviewCacheConfig {

    @Bean("reviewCacheManager")
    public CacheManager reviewCacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                new CaffeineCache("reviewSummary", Caffeine.newBuilder()
                        .maximumSize(20_000)
                        .expireAfterWrite(Duration.ofHours(6))
                        .build()),
                new CaffeineCache("reviewPage", Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(Duration.ofMinutes(10))
                        .build())
        ));
        return manager;
    }
}
