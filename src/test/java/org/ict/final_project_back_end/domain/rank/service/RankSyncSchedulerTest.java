package org.ict.final_project_back_end.domain.rank.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.CronExpression;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 실제 HTTP나 DB를 사용하지 않고 예약 등록과 실행 제어만 검증한다. */
class RankSyncSchedulerTest {
  private final RankSyncService service = mock(RankSyncService.class);
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(Config.class).withBean(RankSyncService.class, () -> service);

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @Import(RankSyncScheduler.class)
  static class Config {}

  @Test
  void defaultAndEitherDisabledFlagRegisterNoTask() {
    for (String[] properties : List.of(new String[]{},
        new String[]{"app.rank.sync-enabled=true"},
        new String[]{"app.rank.schedule-enabled=true"},
        new String[]{"app.rank.sync-enabled=false", "app.rank.schedule-enabled=true"},
        new String[]{"app.rank.sync-enabled=true", "app.rank.schedule-enabled=false"})) {
      context.withPropertyValues(properties).run(c -> {
        assertNull(c.getStartupFailure());
        assertTrue(c.getBeansOfType(RankSyncScheduler.class).isEmpty());
        assertTrue(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().isEmpty());
      });
    }
    verifyNoInteractions(service);
  }

  @Test
  void explicitEnableRegistersOneTaskWithoutStartupCollection() {
    // 테스트 중 실행될 수 없는 미래 예약으로 실제 호출을 방지한다.
    context.withPropertyValues("app.rank.sync-enabled=true", "app.rank.schedule-enabled=true",
        "app.rank.sync-cron=0 0 0 1 1 *").run(c -> {
      assertNull(c.getStartupFailure());
      assertEquals(1,c.getBeansOfType(RankSyncScheduler.class).size());
      assertEquals(1,c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().size());
      verifyNoInteractions(service);
    });
  }

  @Test
  void dashDisablesTriggerEvenWhenFlagsEnabled() {
    context.withPropertyValues("app.rank.sync-enabled=true", "app.rank.schedule-enabled=true",
        "app.rank.sync-cron=-").run(c -> {
      assertNull(c.getStartupFailure());
      assertTrue(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().isEmpty());
      verifyNoInteractions(service);
    });
  }

  @Test
  void defaultScheduleIsDailyAtSixInSeoul() throws Exception {
    var annotation=RankSyncScheduler.class.getMethod("collectLatest").getAnnotation(Scheduled.class);
    assertEquals("Asia/Seoul",annotation.zone());
    assertEquals("${app.rank.sync-cron:0 0 6 * * *}",annotation.cron());
    var now=ZonedDateTime.of(2026,10,21,6,0,0,0,ZoneId.of(annotation.zone()));
    assertEquals(now.plusDays(1),CronExpression.parse("0 0 6 * * *").next(now));
  }

  @Test
  void successRequestsLatestExactlyOnce() {
    when(service.syncWeeklyRanks(null)).thenReturn(List.of(RankSyncServiceTest.snapshot("KR",LocalDate.of(2026,10,13)),RankSyncServiceTest.snapshot("US",LocalDate.of(2026,10,13))));
    new RankSyncScheduler(service).collectLatest();
    verify(service).syncWeeklyRanks(null);
    verifyNoMoreInteractions(service);
  }

  @Test
  void failureReleasesGuardAndNextTriggerRetries() {
    when(service.syncWeeklyRanks(null)).thenThrow(new IllegalStateException("테스트 수집 실패"))
        .thenReturn(List.of(RankSyncServiceTest.snapshot("KR",LocalDate.of(2026,10,13))));
    var scheduler=new RankSyncScheduler(service);
    assertDoesNotThrow(scheduler::collectLatest);
    assertDoesNotThrow(scheduler::collectLatest);
    verify(service,times(2)).syncWeeklyRanks(null);
  }

  @Test
  void overlappingInvocationIsSkipped() throws Exception {
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    when(service.syncWeeklyRanks(null)).thenAnswer(call -> {
      entered.countDown();
      if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("테스트 대기 시간 초과");
      return List.of(RankSyncServiceTest.snapshot("KR",LocalDate.of(2026,10,13)));
    });
    var scheduler=new RankSyncScheduler(service);
    try(var executor=Executors.newSingleThreadExecutor()) {
      var first=executor.submit(scheduler::collectLatest);
      try {
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        scheduler.collectLatest();
        verify(service).syncWeeklyRanks(null);
      } finally {release.countDown();}
      first.get(5,TimeUnit.SECONDS);
    }
  }
}
