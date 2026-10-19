package org.ict.final_project_back_end.domain.aiPc.dto;
import java.util.List;
public record PcPart(String category,String name,long productCode,String manufacturerSource,List<String> compatibilityChecks) {
 public String priceSource(){return "https://prod.danawa.com/info/?pcode="+productCode;}
 public record Offer(PcPart part,Long priceKrw,String status,String note,String checkedAt,Long previousPriceKrw,String previousCheckedAt,Integer observedDropPercent) {}
}
