package org.ict.final_project_back_end.domain.aiPc.service;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import static org.springframework.http.HttpStatus.*;

/** PC 기능 전용 Groq 연결. 다른 AI 기능의 공급자는 변경하지 않는다. */
@Component
public class GroqPcTransport {
 private final String key,model,base;private final long minIntervalMillis;
 private final ObjectMapper json=new ObjectMapper();
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
 private long tokensRemaining=Long.MAX_VALUE,tokenResetAt,nextRequestAt;
 private final java.util.concurrent.locks.ReentrantLock requestLock=new java.util.concurrent.locks.ReentrantLock();
 public GroqPcTransport(String key,String model,String base){this(key,model,base,0);}
 @org.springframework.beans.factory.annotation.Autowired
 public GroqPcTransport(@Value("${app.ai.pc.groq-api-key:}")String key,@Value("${app.ai.pc.groq-model:qwen/qwen3.8-27b}")String model,@Value("${app.ai.pc.groq-base-url:https://api.groq.com}")String base,@Value("${app.ai.pc.groq-min-interval-seconds:60}")long interval){this.key=key;this.model=model;this.base=base.replaceAll("/+$","");this.minIntervalMillis=Math.max(0,Math.min(60,interval))*1000;}
 public boolean configured(){return !key.isBlank();}
 public JsonNode ask(String instructions,List<Map<String,Object>> parts,Map<String,Object> schema){
  if(!configured()||!model.matches("[a-zA-Z0-9/._-]{1,100}"))throw new AiException(SERVICE_UNAVAILABLE,"Groq 연결 설정을 확인해주세요.");
  boolean locked=false;
  try{
   if(!requestLock.tryLock(15,java.util.concurrent.TimeUnit.SECONDS))throw new AiException(SERVICE_UNAVAILABLE,"AI 분석 대기 중이에요. 잠시 후 다시 시도해주세요.");locked=true;
   var content=new ArrayList<Map<String,Object>>();
   for(var part:parts){
    if(part.containsKey("text"))content.add(Map.of("type","text","text",part.get("text")));
    else if(part.get("inlineData") instanceof Map<?,?> image)content.add(Map.of("type","image_url","image_url",Map.of("url","data:"+image.get("mimeType")+";base64,"+image.get("data"))));
    else throw invalid();
   }
   String concise="\n무료 분석 예산을 위해 JSON 내용은 간결하게 작성하세요. summary100자, fhd/qhd/uhd각35자, settings최대2개 각25자, upgradeChecks최대2개 각40자, limitation40자, recommendations최대1개 reason40자/compatibilityNote60자. 사진 판독의 숫자/단위와 미확인 값 처리는 그대로 지키세요. CPU/GPU의 직접 비교 근거가 없으면 충족으로 단정하지 말고 확인 필요라고 쓰세요. CPU/RAM을 묶어 충족이라고 표현하지 마세요.";
   var body=new LinkedHashMap<String,Object>();body.put("model",model);body.put("messages",List.of(Map.of("role","system","content",instructions+concise),Map.of("role","user","content",content)));
   body.put("response_format",Map.of("type","json_schema","json_schema",Map.of("name","pc_diagnosis","strict",true,"schema",schema)));
   boolean hasImage=parts.stream().anyMatch(p->p.containsKey("inlineData"));int outputLimit=hasImage?768:900;
   body.put("max_completion_tokens",outputLimit);body.put("temperature",0.2);
   if(model.startsWith("qwen/"))body.put("reasoning_effort","none");
   long estimate=outputLimit+(hasImage?2048:0)+instructions.length()+json.writeValueAsString(schema).length()/3;
   for(var part:parts)if(part.get("text") instanceof String text)estimate+=text.length()/2;
   long now=System.currentTimeMillis();long wait=Math.max(nextRequestAt-now,tokensRemaining<estimate?tokenResetAt-now:0);
   // 공급자 응답 헤더를 기준으로 같은 PC 공급자의 연속 요청을 간격 조절한다. 429 자동 재시도는 하지 않는다.
   if(wait>0){if(wait>60000)throw new AiException(TOO_MANY_REQUESTS,"Groq 토큰 한도가 회복될 때까지 기다려주세요.");Thread.sleep(wait);}
   var request=HttpRequest.newBuilder(URI.create(base+"/openai/v1/chat/completions")).timeout(Duration.ofSeconds(90)).header("Content-Type","application/json").header("Authorization","Bearer "+key).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
   nextRequestAt=System.currentTimeMillis()+minIntervalMillis;
   tokensRemaining=response.headers().firstValueAsLong("x-ratelimit-remaining-tokens").orElse(Long.MAX_VALUE);
   long reset=resetDelayMillis(response.headers().firstValue("x-ratelimit-reset-tokens").orElse(""));tokenResetAt=System.currentTimeMillis()+(tokensRemaining==Long.MAX_VALUE?0:Math.max(reset,60000));
   if(response.statusCode()!=200){String code="unknown";if(response.body().length<=131072){try{String rawCode=json.readTree(response.body()).path("error").path("code").asText("unknown");if(rawCode.matches("[a-zA-Z0-9_-]{1,64}"))code=rawCode;}catch(Exception ignored){}}log.warn("PC Groq request failed: HTTP {}, code={}",response.statusCode(),code);}
   if(response.statusCode()==429){
    if(response.body().length<=131072){try{String message=json.readTree(response.body()).path("error").path("message").asText();String lower=message.toLowerCase(Locale.ROOT);log.warn("PC Groq quota numbers: limit={}, used={}, requested={}, tokenMinute={}, tokenDay={}, requestMinute={}, requestDay={}",quotaNumber(message,"Limit"),quotaNumber(message,"Used"),quotaNumber(message,"Requested"),lower.contains("tpm")||lower.contains("tokens per minute"),lower.contains("tpd")||lower.contains("tokens per day"),lower.contains("rpm")||lower.contains("requests per minute"),lower.contains("rpd")||lower.contains("requests per day"));}catch(Exception ignored){/* 공급자 본문은 로그로 남기지 않는다. */}}
    throw new AiException(TOO_MANY_REQUESTS,"Groq 이용 한도에 도달했어요. 잠시 후 다시 시도해주세요.");
   }
   if(response.statusCode()==401||response.statusCode()==403)throw new AiException(SERVICE_UNAVAILABLE,"Groq 연결 권한을 확인해주세요.");
   if(response.statusCode()!=200||response.body().length>131072)throw invalid();
   var choice=json.readTree(response.body()).path("choices").path(0);
   if(!"stop".equals(choice.path("finish_reason").asText())||!choice.path("message").path("refusal").asText("").isBlank()){log.warn("PC Groq response rejected: stopped={}, refusal={}","stop".equals(choice.path("finish_reason").asText()),!choice.path("message").path("refusal").asText("").isBlank());throw invalid();}
   var answer=json.readTree(choice.path("message").path("content").asText());if(answer==null||!answer.isObject())throw invalid();return answer;
  }catch(AiException e){throw e;}catch(HttpTimeoutException e){throw new AiException(GATEWAY_TIMEOUT,"AI 분석이 지연되고 있어요. 잠시 후 다시 시도해주세요.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiException(SERVICE_UNAVAILABLE,"AI 분석이 중단됐어요.");}catch(Exception e){log.warn("PC Groq transport/JSON failure: {}",e.getClass().getSimpleName());throw invalid();}finally{if(locked)requestLock.unlock();}
 }
 public static long resetDelayMillis(String value){long total=0;var matcher=java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h)").matcher(value);int end=0;while(matcher.find()){if(matcher.start()!=end)return 0;double factor=switch(matcher.group(2)){case "h"->3600000;case "m"->60000;case "s"->1000;default->1;};total+=(long)Math.ceil(Double.parseDouble(matcher.group(1))*factor);end=matcher.end();if(total>86400000)return 0;}return end==value.length()?total:0;}
 private static String quotaNumber(String message,String field){var match=java.util.regex.Pattern.compile("(?i)\\b"+field+"\\s*:?\\s*([0-9.]{1,12})").matcher(message);return match.find()?match.group(1):"unknown";}
 private static AiException invalid(){return new AiException(BAD_GATEWAY,"AI 답변을 확인하지 못했어요. 다시 시도해주세요.");}
}
