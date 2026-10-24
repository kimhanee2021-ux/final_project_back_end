package org.ict.final_project_back_end.domain.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;

public final class BriefingContent {
  private BriefingContent() {}
  public record Item(String kind, String title, String summary, List<String> sourceIds, String topic) {
    public Item(String kind,String title,String summary,List<String> sourceIds){this(kind,title,summary,sourceIds,null);}
  }
  public static final Map<String,String> TOPICS=Map.ofEntries(
      Map.entry("OPERATION","NOTICE"),Map.entry("MAINTENANCE","NOTICE"),Map.entry("INCIDENT","NOTICE"),Map.entry("CORRECTION","NOTICE"),
      Map.entry("CONTENT","UPDATE"),Map.entry("BALANCE","UPDATE"),Map.entry("FIX","UPDATE"),Map.entry("DEVELOPMENT","UPDATE"),
      Map.entry("EVENT","EVENT"),Map.entry("BENEFIT","EVENT"),Map.entry("TRIAL","EVENT"),Map.entry("RECRUITMENT","EVENT"));
  public static final Set<String> KINDS = Set.of("SCHEDULE", "CHANGE", "CAUTION", "PROGRESS", "CORRECTION", "TOPIC");
  public static BriefingAiClient.Analysis parseAutomatic(String text,Map<String,String> tokenMap) {
    try {
      var json=new ObjectMapper();var root=json.readTree(text);
      var parsed=parse(text,tokenMap.keySet());
      var items=parsed.stream().map(item->new Item(item.kind(),item.title(),item.summary(),item.sourceIds().stream().map(tokenMap::get).toList(),item.topic())).toList();
      if(items.stream().anyMatch(item->!item.title().matches("(?s).*[가-힣].*") || !item.summary().matches("(?s).*[가-힣].*")))throw new IllegalArgumentException("Invalid Korean finding");
      if(items.stream().anyMatch(item->item.topic()==null))throw new IllegalArgumentException("Missing topic");
      List<BriefingAiClient.Translation> translations=new ArrayList<>();
      if(!root.path("translations").isObject() || root.path("translations").size()!=tokenMap.size())throw new IllegalArgumentException("Missing translations");
      for(var entry:tokenMap.entrySet()) {
        var translated=root.path("translations").path(entry.getKey());
        String title=translated.path("title").asText(),summary=translated.path("summary").asText();
        if(summary.isBlank() || summary.length()>1000 || !summary.matches("(?s).*[가-힣].*"))throw new IllegalArgumentException("Invalid Korean summary");
        if(!title.matches("(?s).*[가-힣].*"))title="핵심 내용 · "+summary.substring(0,Math.min(90,summary.length()));
        if(title.isBlank() || title.length()>120)throw new IllegalArgumentException("Invalid Korean title");
        translations.add(new BriefingAiClient.Translation(entry.getValue(),title,summary));
      }
      return new BriefingAiClient.Analysis(items,List.copyOf(translations));
    } catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid automatic response",e);}
  }
  public static String plain(String source) {
    String s = Objects.toString(source, "")
        .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
        .replaceAll("(?is)\\[img[^]]*].*?\\[/img]", " ")
        .replaceAll("<[^>]+>|\\[/?[a-zA-Z*][^]]*]", " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replaceAll("\\s+", " ").strip();
    return s.substring(0, Math.min(4000, s.length()));
  }
  public static boolean safeUrl(String url) {
    try {
      URI u = URI.create(url);
      return "https".equalsIgnoreCase(u.getScheme()) && u.getUserInfo() == null
          && Set.of("steamcommunity.com", "store.steampowered.com", "steamstore-a.akamaihd.net").contains(u.getHost());
    } catch (Exception ignored) { return false; }
  }
  public static List<Item> parse(String text, Set<String> allowedIds) {
    try {
      var root = new ObjectMapper().readTree(text);
      var items = root.path("items");
      if (!items.isArray() || items.size() > 8) throw new IllegalArgumentException("Invalid items");
      List<Item> result = new ArrayList<>();
      for (var n : items) {
        String kind = n.path("kind").asText();
        String title = n.path("title").asText();
        String summary = n.path("summary").asText();
        String topic=n.hasNonNull("topic")?n.path("topic").asText():null;
        if(topic!=null && !TOPICS.containsKey(topic))throw new IllegalArgumentException("Unknown topic");
        if (!KINDS.contains(kind) || title.isBlank() || title.length() > 120 || summary.isBlank() || summary.length() > 1000)
          throw new IllegalArgumentException("Invalid item");
        var sources = n.path("sourceIds");
        if (!sources.isArray() || sources.isEmpty() || sources.size() > 5) throw new IllegalArgumentException("Missing sources");
        List<String> ids = new ArrayList<>();
        for (var source : sources) {
          if (!source.isTextual() || !allowedIds.contains(source.asText())) throw new IllegalArgumentException("Unknown source");
          ids.add(source.asText());
        }
        result.add(new Item(kind, title, summary, List.copyOf(new LinkedHashSet<>(ids)),topic));
      }
      return List.copyOf(result);
    } catch (IllegalArgumentException e) { throw e; }
      catch (Exception e) { throw new IllegalArgumentException("Invalid AI JSON", e); }
  }
}
