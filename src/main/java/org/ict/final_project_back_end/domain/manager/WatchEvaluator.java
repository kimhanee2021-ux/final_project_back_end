package org.ict.final_project_back_end.domain.manager;
import java.util.*;
public class WatchEvaluator {
 public static double won(long minor){return minor/100.0;}
 public static boolean matches(long minor,long targetWon){return minor<=Math.multiplyExact(targetWon,100);}
 public static long total(List<ProductPrice> products,Set<Long> owned){
  Set<Long> ids=new HashSet<>(owned);products.forEach(p->ids.add(p.appId()));long total=0;
  for(var p:products){
   if(!Set.of("game","dlc").contains(p.type()))throw new IllegalArgumentException("본편과 DLC만 선택해주세요. 사운드트랙 등은 지원하지 않아요.");
   if(p.type().equals("dlc")&&(p.parentAppId()==null||!ids.contains(p.parentAppId())))throw new IllegalArgumentException("DLC에 필요한 본편을 보유 게임이나 구매 목록에 추가해주세요.");
   if(owned.contains(p.appId()))continue;
   if(!"KRW".equals(p.currency())||p.minorPrice()==null||p.minorPrice()<0)throw new IllegalArgumentException("한국 상점 가격을 확인할 수 없는 상품이 있어요.");
   total=Math.addExact(total,p.minorPrice());
  }return total;
 }
}
