package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.config.*;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsSyncResult;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameNewsSyncSchedulerTest {
  @Configuration(proxyBeanMethods = false) @EnableScheduling static class Scheduling { }

  @Test void disabledByDefaultDoesNotRegisterScheduler() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(GameNewsSyncConfiguration.class, GameNewsSyncScheduler.class, Scheduling.class);
      context.refresh();
      assertTrue(context.getBeansOfType(GameNewsSyncScheduler.class).isEmpty());
      assertFalse(context.containsBean("gameNewsTaskScheduler"));
    }
  }

  @Test void enabledSchedulerActuallyExecutesRepeatedlyOnDedicatedThread() throws Exception {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
          "app.news.sync.enabled", "true", "app.news.sync.poll-interval", "PT0.1S", "app.news.sync.initial-delay", "PT0.1S")));
      var service = mock(GameNewsSyncService.class);
      var latch = new CountDownLatch(2);
      var threads = new ConcurrentLinkedQueue<String>();
      when(service.runOnce()).thenAnswer(call -> { threads.add(Thread.currentThread().getName()); latch.countDown(); return new GameNewsSyncResult(false, false, List.of()); });
      context.registerBean(GameNewsSyncService.class, () -> service);
      context.register(GameNewsSyncConfiguration.class, GameNewsSyncScheduler.class, Scheduling.class);
      context.refresh();
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertTrue(threads.stream().allMatch(name -> name.startsWith("game-news-")));
      assertNotNull(context.getBean(GameNewsSyncScheduler.class).lastResult());
    }
  }

  @Test void overlappingCycleSkipsAndGuardIsReleasedAfterCompletion() throws Exception {
    var collection = mock(GameNewsCollectionService.class);
    var news = mock(GameNewsRepository.class);
    var ai = mock(GameNewsBriefingService.class);
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    when(collection.collectIfDue(570, 5)).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return Optional.empty(); });
    var service = new GameNewsSyncService(new GameNewsSyncProperties(List.of(570L), 5, 5, 0), collection, ai, news);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var first = pool.submit(service::runOnce);
      try { assertTrue(entered.await(5, TimeUnit.SECONDS)); assertTrue(service.runOnce().alreadyRunning()); }
      finally { release.countDown(); }
      assertFalse(first.get(5, TimeUnit.SECONDS).alreadyRunning());
      assertFalse(service.runOnce().alreadyRunning());
    }
  }

  @Test void failedScheduledTickDoesNotPreventNextExecution() {
    var service = mock(GameNewsSyncService.class);
    when(service.runOnce()).thenThrow(new IllegalStateException()).thenReturn(new GameNewsSyncResult(false, false, List.of()));
    var scheduler = new GameNewsSyncScheduler(service);
    scheduler.tick(); scheduler.tick();
    assertEquals(1, scheduler.completedRuns());
  }
}
