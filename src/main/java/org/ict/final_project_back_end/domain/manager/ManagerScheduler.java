package org.ict.final_project_back_end.domain.manager;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class ManagerScheduler {
 private final ManagerStore store;private final ManagerRepository repo;private long cursor=0;
 public ManagerScheduler(ManagerStore s,ManagerRepository r){store=s;repo=r;}
 @Scheduled(fixedDelayString="${app.manager.scan-delay-ms:60000}",initialDelayString="${app.manager.scan-delay-ms:60000}")
 public void scan(){
  var list=repo.findByKindOrderByIdAsc("watch");int processed=0;
  for(var r:list){if(r.id<=cursor)continue;cursor=r.id;try{var w=store.watch(r);if(w.enabled()&&(w.lastChecked().isBlank()||Instant.parse(w.lastChecked()).isBefore(Instant.now().minusSeconds(store.intervalMinutes()*60))))store.check(r.userId,r.id);}catch(Exception e){/* A failed watch must not prevent others from being checked. */}if(++processed>=15)return;}
  cursor=0;
 }
}
