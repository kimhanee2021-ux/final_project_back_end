package org.ict.final_project_back_end.domain.manager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ManagerPriceTest {
 @Test void krwUsesMinorUnitsAndRequiresAllItems(){assertEquals(5500,WatchEvaluator.won(550000));assertTrue(WatchEvaluator.matches(1500000,15000));assertFalse(WatchEvaluator.matches(1500001,15000));}
 @Test void missingBaseAndCurrencyMismatchAreRejected(){
  var dlc=new ProductPrice(2,"DLC","dlc",1L,"KRW",10000L,"",List.of(),"now");
  assertThrows(IllegalArgumentException.class,()->WatchEvaluator.total(List.of(dlc),Set.of()));
  assertEquals(10000,WatchEvaluator.total(List.of(dlc),Set.of(1L)));
  var usd=new ProductPrice(3,"USD","game",null,"USD",100L,"",List.of(),"now");assertThrows(IllegalArgumentException.class,()->WatchEvaluator.total(List.of(usd),Set.of()));
 }
 @Test void ownedItemsDoNotChargeAndUnknownPriceCannotTrigger(){var p=new ProductPrice(1,"game","game",null,"KRW",null,"",List.of(),"now");assertThrows(IllegalArgumentException.class,()->WatchEvaluator.total(List.of(p),Set.of()));assertEquals(0,WatchEvaluator.total(List.of(p),Set.of(1L)));}
}
