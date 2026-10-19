package org.ict.final_project_back_end.domain.aiPc.service;

import java.util.regex.Pattern;

/** 공개 상품 메타정보만 파싱한다. 현금가·멤버십·적립금을 일반 판매가에 섞지 않는다. */
public final class PcPartPrices {
 private PcPartPrices() {}
 public record Parsed(Long price,String status,String note) {}
 public static Parsed parse(String html,String expectedName) {
  String title=meta(html,"og:title"), description=meta(html,"og:description");
  if(!normalize(title.replaceFirst("^\\[다나와\\]\\s*","")).equals(normalize(expectedName)))throw new IllegalArgumentException("상품명 또는 용량 불일치");
  var sellers=Pattern.compile("판매점\\s*:\\s*([0-9,]+)개").matcher(html);
  if(!sellers.find())return new Parsed(null,"UNVERIFIED","판매점 수를 확인하지 못했어요.");
  int count=Integer.parseInt(sellers.group(1).replace(",",""));
  if(count==0)return new Parsed(null,"UNAVAILABLE","가격 비교 가능한 판매점이 없어요.");
  var price=Pattern.compile("^최저가\\s+([0-9,]+)원(?:[,\\s]|$)").matcher(description);
  if(!price.find())return new Parsed(null,"UNVERIFIED","일반 최저가를 확인하지 못했어요.");
  long won=Long.parseLong(price.group(1).replace(",",""));
  if(won<=0||won>100000000)return new Parsed(null,"UNAVAILABLE","유효한 판매가가 없어요.");
  return new Parsed(won,"OBSERVED","다나와 일반 최저가 표기. 배송비·쿠폰·카드·재고는 판매처에서 확인해 주세요.");
 }
 private static String normalize(String s){return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 private static String meta(String html,String property){var tag=Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);while(tag.find()){String t=tag.group();if(Pattern.compile("(?i)property\\s*=\\s*[\"']"+Pattern.quote(property)+"[\"']").matcher(t).find()){var content=Pattern.compile("(?is)content\\s*=\\s*([\"'])(.*?)\\1").matcher(t);if(content.find())return content.group(2).replace("&amp;","&").trim();}}return "";}
 public static Integer dropPercent(Long previous,Long current){if(previous==null||current==null||previous<=0||current>=previous)return null;int percent=(int)((previous-current)*100/previous);return percent>0?percent:null;}
}
