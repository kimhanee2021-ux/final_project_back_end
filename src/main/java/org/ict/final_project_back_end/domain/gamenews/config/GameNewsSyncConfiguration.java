package org.ict.final_project_back_end.domain.gamenews.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GameNewsSyncProperties.class)
public class GameNewsSyncConfiguration {
  // Keep Boot's default scheduler for other domains; AI requests run on this dedicated thread.
  @Bean(name = "gameNewsTaskScheduler", defaultCandidate = false)
  @ConditionalOnProperty(prefix = "app.news.sync", name = "enabled", havingValue = "true")
  public ThreadPoolTaskScheduler gameNewsTaskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("game-news-");
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.setAwaitTerminationSeconds(30);
    return scheduler;
  }
}
