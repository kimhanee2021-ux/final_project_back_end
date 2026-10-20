package org.ict.final_project_back_end.domain.aiCommon.service;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import static org.springframework.http.HttpStatus.*;

@Component
public class AiRateLimit {
  private record Window(long minute,int count) { }
  private final Map<Long,Window> windows=new HashMap<>();
  private final java.util.Set<Long> active=new java.util.HashSet<>();
  public synchronized void begin(Long userId){
    begin(userId,6,windows);
  }
  private void begin(Long userId,int maximum,Map<Long,Window> windows){
    long minute=System.currentTimeMillis()/60000;
    windows.entrySet().removeIf(e->e.getValue().minute()<minute);
    Window previous=windows.get(userId);
    if(active.contains(userId) || (previous!=null && previous.count()>=maximum) || windows.size()>=10000)
      throw new AiException(TOO_MANY_REQUESTS,"요청이 많습니다. 잠시 후 다시 시도해주세요.");
    windows.put(userId,new Window(minute,previous==null ? 1 : previous.count()+1));
    active.add(userId);
  }
  public synchronized void end(Long userId){active.remove(userId);}
}
