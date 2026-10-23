package org.ict.final_project_back_end.domain.gamenews.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NewsVoiceCorrectionTest {
  @Test void lastCorrectionWinsWithoutRetainingRejectedCategoryOrGame() {
    for (String text : new String[]{"도타2 패치 아니 이벤트 알려줘", "도타2 패치 대신 이벤트", "도타2 패치 말고 이벤트"}) {
      var slots = NewsVoiceRules.extract(text);
      assertNotNull(slots); assertEquals("도타2", slots.gameQuery()); assertEquals("EVENT", slots.category()); assertFalse(slots.multipleCategories());
    }
    var game = NewsVoiceRules.extract("도타2 말고 카스2 패치 알려줘");
    assertEquals("카스2", game.gameQuery()); assertEquals("PATCH", game.category());
    assertEquals("EVENT", NewsVoiceRules.extract("그거 말고 이벤트 알려줘").category());
    assertTrue(NewsVoiceRules.incompleteCorrection("아니"));
    assertTrue(NewsVoiceRules.incompleteCorrection("도타2 말고"));
  }
  @Test void ambiguousOrNestedCorrectionsUseGeminiInsteadOfGuessing() {
    assertNull(NewsVoiceRules.extract("아니 그거 말고 아까 이야기한 게임에서 이번에 바뀐 내용으로 알려줘"));
    assertNull(NewsVoiceRules.extract("도타2 패치 아니 이벤트 아니 개발 소식"));
    assertNull(NewsVoiceRules.extract("그거 말고"));
    assertEquals("STOP", NewsVoiceRules.extract("중지").command());
    assertTrue(NewsVoiceRules.extract("도타2 패치랑 이벤트 알려줘").multipleCategories());
  }
}
