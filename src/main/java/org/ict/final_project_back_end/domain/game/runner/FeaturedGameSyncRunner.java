package org.ict.final_project_back_end.domain.game.runner;

import lombok.RequiredArgsConstructor;
import org.ict.final_project_back_end.domain.game.service.FeaturedGameSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/*
 * 서버 기동 시 스팀 featured/featuredcategories 캐시를 한 번 받아 둔다.
 * 평소(새벽 4:10)까지 기다리지 않고 바로 데이터를 보고 싶은 개발 환경에서 쓰는 용도.
 * - app.featured.sync-on-startup=true 일 때만 동작 (기본 true)
 * - 성인 콘텐츠 판정으로 게임마다 스팀을 한 번씩 더 조회해 오래 걸리므로, 기동을 막지 않게 별도 스레드에서 돌린다
 * - 스팀 호출이 실패해도 FeaturedGameSyncService 안에서 처리되므로 기동이 막히지 않는다
 */
@Component
@RequiredArgsConstructor
public class FeaturedGameSyncRunner implements ApplicationRunner {

  private final FeaturedGameSyncService featuredGameSyncService;

  @Value("${app.featured.sync-on-startup:true}")
  private boolean syncOnStartup;

  @Override
  public void run(ApplicationArguments args) {
    if (syncOnStartup) {
      CompletableFuture.runAsync(featuredGameSyncService::refreshAll);
    }
  }
}
