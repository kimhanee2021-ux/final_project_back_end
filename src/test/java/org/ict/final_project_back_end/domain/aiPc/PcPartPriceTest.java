package org.ict.final_project_back_end.domain.aiPc;
import org.ict.final_project_back_end.domain.aiPc.service.PcPartPrices;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PcPartPriceTest {
 private String page(String name,String description,int sellers){return "<meta property=\"og:title\" content=\"[다나와] "+name+"\"><meta property=\"og:description\" content=\""+description+"\"><li>판매점 : "+sellers+"개</li>";}
 @Test void extractsOnlyOrdinaryPriceNotCashOrPoints(){assertEquals(326890L,PcPartPrices.parse(page("SSD 1TB","최저가 326,890원, 현금최저가: 312,500원",20),"SSD 1TB").price());}
 @Test void zeroPriceAndZeroSellersAreUnavailable(){assertNull(PcPartPrices.parse(page("GPU 6GB","최저가 0원",0),"GPU 6GB").price());}
 @Test void differentCapacityOrModelIsRejected(){assertThrows(IllegalArgumentException.class,()->PcPartPrices.parse(page("SSD 2TB","최저가 300,000원",10),"SSD 1TB"));}
 @Test void motherboardVariantCannotBeSubstituted(){assertThrows(IllegalArgumentException.class,()->PcPartPrices.parse(page("MSI PRO B760M-A WIFI DDR4","최저가 100,000원",10),"MSI PRO B760M-A WIFI"));}
 @Test void unrelatedNumbersCannotBecomePrice(){assertNull(PcPartPrices.parse(page("SSD 1TB","적립 4% 300,000원",10),"SSD 1TB").price());}
 @Test void previousObservedPriceIsNotRetailDiscount(){assertEquals(10,PcPartPrices.dropPercent(100000L,90000L));assertNull(PcPartPrices.dropPercent(null,90000L));assertNull(PcPartPrices.dropPercent(90000L,100000L));}
}
