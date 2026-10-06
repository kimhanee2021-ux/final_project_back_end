package org.ict.final_project_back_end.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// 게임 전체 동기화는 며칠씩 걸리므로 스케줄러 스레드를 막지 않도록 @Async를 함께 켠다
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
