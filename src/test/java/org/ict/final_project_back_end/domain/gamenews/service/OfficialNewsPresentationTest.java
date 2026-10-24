package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.LocalDateTime;
import java.util.List;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficialNewsPresentationTest {
  @Test void classifiesFourGroupsAndPrefersSteamPatchTag() {
    assertCategory("Counter-Strike 2 Update", GameNewsCategory.PATCH);
    assertCategory("핫픽스 안내", GameNewsCategory.PATCH);
    assertCategory("Summer Championship Finals", GameNewsCategory.EVENT);
    assertCategory("기념 이벤트", GameNewsCategory.EVENT);
    assertCategory("Developer Update: Summer", GameNewsCategory.DEV_NEWS);
    assertCategory("개발 일지와 로드맵", GameNewsCategory.DEV_NEWS);
    assertCategory("A message from the team", GameNewsCategory.NOTICE);
    assertEquals(GameNewsCategory.PATCH, OfficialNewsClassifier.classify(item("Summer Event", List.of("patchnotes"))));
  }

  @Test void doesNotClassifyFromIncidentalWordsInBody() {
    var source = new SteamNewsItem(730, "1", "A message from the team", "No event or patch today.",
        "https://steamcommunity.com/a", LocalDateTime.now());
    assertEquals(GameNewsCategory.NOTICE, OfficialNewsClassifier.classify(source));
  }

  @Test void makesReadableTextWithoutHtmlOrBbcodeAndKeepsLiteralSectionNames() {
    String raw = "[h2]Update &amp; fixes[/h2][p]\\[ MAP SCRIPTING ][/p][list][*][p]Fixed a bug[/p][/*][/list]"
        + "[url=https://example.test]More information[/url][img]https://example.test/a.png[/img]"
        + "<script>alert(1)</script><p>Hello<br>World</p>";
    String text = OfficialNewsText.plain(raw);
    assertTrue(text.contains("Update & fixes"));
    assertTrue(text.contains("[ MAP SCRIPTING ]"));
    assertTrue(text.contains("• Fixed a bug"));
    assertTrue(text.contains("Hello\nWorld"));
    for (String removed : List.of("[p]", "[url", "[/*]", "[list]", "<script>", "alert(1)", "a.png"))
      assertFalse(text.contains(removed), removed);
  }

  private void assertCategory(String title, GameNewsCategory expected) {
    assertEquals(expected, OfficialNewsClassifier.classify(item(title, List.of())));
  }
  private SteamNewsItem item(String title, List<String> tags) {
    return new SteamNewsItem(730, "1", title, "Body", "https://steamcommunity.com/a", LocalDateTime.now(), tags);
  }
}
