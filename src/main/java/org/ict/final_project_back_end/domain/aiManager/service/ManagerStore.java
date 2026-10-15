package org.ict.final_project_back_end.domain.aiManager.service;
import org.ict.final_project_back_end.domain.aiManager.dto.CollectionDraft;
import org.ict.final_project_back_end.domain.aiManager.dto.Watch;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiWorldcup.service.WorldcupCatalog;
import org.ict.final_project_back_end.domain.aiManager.entity.ManagerRecord;
import org.ict.final_project_back_end.domain.aiManager.repository.ManagerRepository;
import org.ict.final_project_back_end.domain.aiManager.dto.ProductPrice;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import static org.springframework.http.HttpStatus.*;
@Service
public class ManagerStore {


 private final ManagerRepository repo;private final UserRepository users;private final LibraryGameRepository library;private final SteamProductClient steam;private final WorldcupCatalog catalog;
 @org.springframework.beans.factory.annotation.Value("${app.manager.check-interval-minutes:60}") private long intervalMinutes=60;
 public long intervalMinutes(){return Math.max(1,intervalMinutes);}
 private final ObjectMapper json=new ObjectMapper();
 public ManagerStore(ManagerRepository r,UserRepository u,LibraryGameRepository l,SteamProductClient s,WorldcupCatalog c){repo=r;users=u;library=l;steam=s;catalog=c;}
 public void auth(Long id){if(id==null||!users.existsById(id))throw new AiException(UNAUTHORIZED,"로그인하고 이용해주세요.");}
 public Object decode(ManagerRecord r){try{return Map.of("id",r.id,"kind",r.kind,"data",json.readValue(r.payload,Map.class));}catch(Exception e){throw new AiException(INTERNAL_SERVER_ERROR,"저장된 자료를 읽지 못했어요.");}}
 private String encode(Object data){try{return json.writeValueAsString(data);}catch(Exception e){throw new AiException(BAD_REQUEST,"입력 내용을 확인해주세요.");}}
 public Watch watch(ManagerRecord r){try{return json.readValue(r.payload,Watch.class);}catch(Exception e){throw new AiException(INTERNAL_SERVER_ERROR,"구매 조건을 읽지 못했어요.");}}
 public ManagerRecord ownedRecord(Long id,Long user){return repo.findByIdAndUserId(id,user).orElseThrow(()->new AiException(NOT_FOUND,"항목을 찾을 수 없어요."));}
 public List<Map<String,Object>> owned(Long user){
  var result=new LinkedHashMap<Long,Map<String,Object>>();
  for(var g:library.findAllByUserIdOrderByNameAsc(user))result.put(g.getAppId(),Map.of("appId",g.getAppId(),"name",g.getName(),"minutes",g.getPlaytimeForever(),"source","Steam 동기화"));
  for(var r:repo.findByUserIdAndKind(user,"owned")){try{var p=json.readValue(r.payload,ProductPrice.class);result.putIfAbsent(p.appId(),Map.of("appId",p.appId(),"name",p.name(),"source","직접 등록"));}catch(Exception e){throw new AiException(INTERNAL_SERVER_ERROR,"보유 목록을 읽지 못했어요.");}}
  return List.copyOf(result.values());
 }
 public Set<Long> ownedIds(Long user){var result=new HashSet<Long>();for(var g:owned(user))result.add(((Number)g.get("appId")).longValue());return result;}
 @Transactional(readOnly=true) public Map<String,Object> state(Long user){auth(user);return Map.of("owned",owned(user),"records",repo.findByUserIdOrderByIdDesc(user).stream().map(this::decode).toList(),"catalog",catalog.games(),"checkIntervalMinutes",intervalMinutes());}
 @Transactional public Object addOwned(Long user,long id){auth(user);if(ownedIds(user).contains(id))throw new AiException(CONFLICT,"이미 보유 목록에 있어요.");if(repo.findByUserIdAndKind(user,"owned").size()>=500)throw new AiException(BAD_REQUEST,"직접 등록은 500개까지 가능해요.");return decode(repo.save(new ManagerRecord(user,"owned",encode(steam.product(id)))));}
 @Transactional public Object collection(Long user,CollectionDraft draft){auth(user);validateText(draft.name(),50);validateText(draft.explanation(),500);if(draft.appIds()==null||draft.appIds().isEmpty()||draft.appIds().size()>100||new HashSet<>(draft.appIds()).size()!=draft.appIds().size()||!ownedIds(user).containsAll(draft.appIds()))throw new AiException(BAD_REQUEST,"내가 보유한 게임만 컬렉션에 넣을 수 있어요.");for(var existing:repo.findByUserIdAndKind(user,"collection")){if(existing.payload.equals(encode(draft)))return decode(existing);}if(repo.findByUserIdAndKind(user,"collection").size()>=50)throw new AiException(BAD_REQUEST,"컬렉션은 50개까지 저장할 수 있어요.");return decode(repo.save(new ManagerRecord(user,"collection",encode(draft))));}
 @Transactional public Object rename(Long user,Long id,String name){auth(user);validateText(name,50);var r=ownedRecord(id,user);if(!r.kind.equals("collection"))throw new AiException(BAD_REQUEST,"컬렉션을 선택해주세요.");try{var d=json.readValue(r.payload,CollectionDraft.class);r.payload=encode(new CollectionDraft(name,d.explanation(),d.appIds()));return decode(repo.save(r));}catch(AiException e){throw e;}catch(Exception e){throw new AiException(BAD_REQUEST,"이름을 바꾸지 못했어요.");}}
 @Transactional public Object saveWatch(Long user,Long id,List<Long> ids,long target,String label){auth(user);validateText(label,100);if(ids==null||ids.isEmpty()||ids.size()>6||new HashSet<>(ids).size()!=ids.size()||target<1||target>10000000)throw new AiException(BAD_REQUEST,"상품은 1~6개, 목표 금액은 1~1천만 원으로 입력해주세요.");var prices=ids.stream().map(steam::product).toList();Set<Long> owned=ownedIds(user);if(owned.containsAll(ids))throw new AiException(BAD_REQUEST,"선택한 상품을 모두 보유하고 있어요.");long total;try{total=WatchEvaluator.total(prices,owned);}catch(IllegalArgumentException e){throw new AiException(BAD_REQUEST,e.getMessage());}
  boolean alreadyMatched=false;ManagerRecord r;if(id==null){for(var existing:repo.findByUserIdAndKind(user,"watch")){var previous=watch(existing);if(previous.appIds().equals(ids)&&previous.targetWon()==target&&previous.label().equals(label))return decode(existing);}if(repo.findByUserIdAndKind(user,"watch").size()>=20)throw new AiException(BAD_REQUEST,"구매 대기는 20개까지 저장할 수 있어요.");r=new ManagerRecord(user,"watch","");}else{r=ownedRecord(id,user);if(!r.kind.equals("watch"))throw new AiException(BAD_REQUEST,"구매 조건을 선택해주세요.");var previous=watch(r);alreadyMatched=Boolean.TRUE.equals(previous.matched())&&previous.enabled()&&previous.appIds().equals(ids)&&previous.targetWon()==target;}
  r.payload=encode(new Watch(List.copyOf(ids),target,label,true,Instant.now().toString(),WatchEvaluator.matches(total,target),"",prices,total));r=repo.save(r);
  if(WatchEvaluator.matches(total,target)&&!alreadyMatched)notify(user,r.id,label,total);return decode(r);
 }
 private void notify(Long user,Long watchId,String label,long total){repo.save(new ManagerRecord(user,"notification",encode(Map.of("watchId",watchId,"title",label,"message","구매 조건을 충족했어요. 보유한 상품을 제외한 개별 상품 합계 "+java.text.NumberFormat.getNumberInstance(Locale.KOREA).format(WatchEvaluator.won(total))+"원이에요. 상점에서 최종 가격을 확인해주세요.","createdAt",Instant.now().toString(),"read",false))));}
 @Transactional public Object read(Long user,Long id){auth(user);var r=ownedRecord(id,user);if(!r.kind.equals("notification"))throw new AiException(BAD_REQUEST,"알림을 선택해주세요.");try{var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(r.payload);node.put("read",true);r.payload=json.writeValueAsString(node);return decode(repo.save(r));}catch(Exception e){throw new AiException(INTERNAL_SERVER_ERROR,"읽음 상태를 저장하지 못했어요.");}}
 @Transactional public void delete(Long user,Long id){auth(user);repo.delete(ownedRecord(id,user));}
 @Transactional public Object pause(Long user,Long id,boolean enabled){auth(user);var r=ownedRecord(id,user);if(!r.kind.equals("watch"))throw new AiException(BAD_REQUEST,"구매 조건을 선택해주세요.");var w=watch(r);r.payload=encode(new Watch(w.appIds(),w.targetWon(),w.label(),enabled,enabled&&!w.enabled()?"":w.lastChecked(),enabled&&!w.enabled()?false:w.matched(),w.error(),w.prices(),w.totalMinor()));return decode(repo.save(r));}
 @Transactional public Object check(Long user,Long id){auth(user);var r=ownedRecord(id,user);if(!r.kind.equals("watch"))throw new AiException(BAD_REQUEST,"구매 조건을 선택해주세요.");var w=watch(r);if(!w.enabled())throw new AiException(BAD_REQUEST,"감시를 켠 뒤 확인해주세요.");try{var prices=w.appIds().stream().map(steam::product).toList();var owns=ownedIds(user);if(owns.containsAll(w.appIds()))throw new IllegalArgumentException("선택한 상품을 모두 보유했어요. 구매 대기에서 삭제해주세요.");long total=WatchEvaluator.total(prices,owns);boolean match=WatchEvaluator.matches(total,w.targetWon());if(match&&!Boolean.TRUE.equals(w.matched()))notify(user,r.id,w.label(),total);r.payload=encode(new Watch(w.appIds(),w.targetWon(),w.label(),true,Instant.now().toString(),match,"",prices,total));}catch(AiException|IllegalArgumentException e){r.payload=encode(new Watch(w.appIds(),w.targetWon(),w.label(),true,Instant.now().toString(),w.matched(),e.getMessage(),w.prices(),w.totalMinor()));}return decode(repo.save(r));}
 private void validateText(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw new AiException(BAD_REQUEST,"이름과 설명 길이를 확인해주세요.");}
}
