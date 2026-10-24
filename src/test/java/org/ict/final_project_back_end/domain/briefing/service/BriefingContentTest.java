package org.ict.final_project_back_end.domain.briefing.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BriefingContentTest {
  @Test void automaticResultRestoresSourceIdsAndRequiresKoreanTranslations() {
    var result="{\"translations\":{\"s1\":{\"title\":\"새 업데이트\",\"summary\":\"패치가 발표됐어요.\"}},\"items\":[{\"kind\":\"CHANGE\",\"topic\":\"FIX\",\"title\":\"수정 안내\",\"summary\":\"수정이 발표됐어요.\",\"sourceIds\":[\"s1\"]}]}";
    var parsed=BriefingContent.parseAutomatic(result,java.util.Map.of("s1","1234567890123456789"));
    assertEquals("1234567890123456789",parsed.translations().getFirst().gid());
    assertEquals("1234567890123456789",parsed.items().getFirst().sourceIds().getFirst());
    assertThrows(IllegalArgumentException.class,()->BriefingContent.parseAutomatic(result.replace("수정이 발표됐어요.","English only"),java.util.Map.of("s1","1")));
    assertThrows(IllegalArgumentException.class,()->BriefingContent.parseAutomatic(result.replace("패치가 발표됐어요.","English only"),java.util.Map.of("s1","1")));
  }
  @Test void identicalRawSourceKeepsExistingTranslation() {
    var row=new org.ict.final_project_back_end.domain.briefing.entity.OfficialNews(1L,"게임","1");
    var now=java.time.Instant.now();
    row.refresh("원문","본문","https://steamcommunity.com/",now,now,"digest");
    row.translate("한국어 제목","한국어 설명");
    row.refresh("원문","형식만 달라진 본문","https://steamcommunity.com/",now,now,"digest");
    assertEquals("한국어 제목",row.getKoreanTitle());assertEquals("본문",row.getContent());
    row.refresh("원문","수정된 본문","https://steamcommunity.com/",now,now,"changed");
    assertNull(row.getKoreanTitle());
  }
  @Test void preservesShortTopicsAndRejectsInventedTopics() {
    var good="{\"items\":[{\"kind\":\"CAUTION\",\"topic\":\"MAINTENANCE\",\"title\":\"점검\",\"summary\":\"점검 발표\",\"sourceIds\":[\"1\"]}]}";
    assertEquals("MAINTENANCE", BriefingContent.parse(good,Set.of("1")).getFirst().topic());
    assertThrows(IllegalArgumentException.class,()->BriefingContent.parse(good.replace("MAINTENANCE","MADE_UP"),Set.of("1")));
  }
  @Test void stripsExecutableMarkupAndCapsSourceText() {
    String text = BriefingContent.plain("<script>bad()</script>[h1]Patch[/h1]<p>Fixed &amp; tested</p>");
    assertFalse(text.contains("bad()"));
    assertTrue(text.contains("Patch"));
    assertTrue(text.contains("Fixed & tested"));
    assertEquals(4000, BriefingContent.plain("x".repeat(5000)).length());
  }
  @Test void rejectsInventedAndMissingSourcesAndUnknownCategories() {
    assertThrows(IllegalArgumentException.class, () -> BriefingContent.parse("{\"items\":[{\"kind\":\"CHANGE\",\"title\":\"변경\",\"summary\":\"설명\",\"sourceIds\":[\"999\"]}]}", Set.of("1")));
    assertThrows(IllegalArgumentException.class, () -> BriefingContent.parse("{\"items\":[{\"kind\":\"CHANGE\",\"title\":\"변경\",\"summary\":\"설명\",\"sourceIds\":[]}]}", Set.of("1")));
    assertThrows(IllegalArgumentException.class, () -> BriefingContent.parse("{\"items\":[{\"kind\":\"FAKE\",\"title\":\"변경\",\"summary\":\"설명\",\"sourceIds\":[\"1\"]}]}", Set.of("1")));
  }
  @Test void acceptsEmptyAndGroundedResults() {
    assertEquals(0, BriefingContent.parse("{\"items\":[]}", Set.of("1")).size());
    var result = BriefingContent.parse("{\"items\":[{\"kind\":\"CHANGE\",\"title\":\"변경\",\"summary\":\"수정 발표\",\"sourceIds\":[\"1\"]}]}", Set.of("1"));
    assertEquals("1", result.getFirst().sourceIds().getFirst());
  }
  @Test void rejectsUnsafeLinks() {
    assertFalse(BriefingContent.safeUrl("javascript:alert(1)"));
    assertFalse(BriefingContent.safeUrl("https://evil.example/path"));
    assertTrue(BriefingContent.safeUrl("https://steamcommunity.com/games/570/announcements/detail/123"));
  }
}
