package org.ict.final_project_back_end.domain.aiPc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.ict.final_project_back_end.domain.aiPc.service.PcRetailPrices;
class PcRetailPricesTest {
 private String page(String name,String price,String meta,String stock){return "<meta property=\"product:price:amount\" content=\""+meta+"\"><meta property=\"product:price:currency\" content=\"KRW\"><script type=\"application/ld+json\">{\"@graph\":[{\"@type\":\"Product\",\"name\":\""+name+" - COMTRIX\",\"offers\":{\"@type\":\"Offer\",\"price\":\""+price+"\",\"priceCurrency\":\"KRW\",\"priceValidUntil\":\"2099-12-31\",\"availability\":\"https://schema.org/"+stock+"\",\"itemCondition\":\"NewCondition\"}}]}</script>";}
 @Test void readsMatchingOrdinaryKrwOffer(){assertEquals(67800L,PcRetailPrices.parseComtrix(page("600W 실버 ATX3.1","67800","67800","InStock"),"600W 실버 ATX3.1").price());}
 @Test void rejectsDifferentPowerVariant(){assertNull(PcRetailPrices.parseComtrix(page("700W 실버 ATX3.1","67800","67800","InStock"),"600W 실버 ATX3.1").price());}
 @Test void rejectsMetadataMismatchAndUnavailableStock(){assertNull(PcRetailPrices.parseComtrix(page("600W","67800","60000","InStock"),"600W").price());assertNull(PcRetailPrices.parseComtrix(page("600W","67800","67800","OutOfStock"),"600W").price());}
 @Test void rejectsExpiredPriceAndForeignCurrency(){String page=page("600W","67800","67800","InStock");assertNull(PcRetailPrices.parseComtrix(page.replace("2099-12-31","2000-01-01"),"600W").price());assertNull(PcRetailPrices.parseComtrix(page.replace("KRW","USD"),"600W").price());}
 @Test void refusesMalformedAndAmbiguousProducts(){assertNull(PcRetailPrices.parseComtrix("<h1>600W</h1>67800원","600W").price());assertNull(PcRetailPrices.parseComtrix(page("600W","67800","67800","InStock").repeat(2),"600W").price());}
 @Test void acceptsOnlyFormattingDifferencesNotCapacityOrProductSuffix(){String page=page("삼성전자 990 EVO PLUS M.2 NVMe 1TB","342400","342400","InStock");assertEquals(342400L,PcRetailPrices.parseComtrix(page,"삼성전자 990 EVO Plus M.2 NVMe (1TB)").price());assertNull(PcRetailPrices.parseComtrix(page,"삼성전자 990 EVO Plus M.2 NVMe (2TB)").price());assertNull(PcRetailPrices.parseComtrix(page,"삼성전자 990 EVO M.2 NVMe (1TB)").price());}
}
