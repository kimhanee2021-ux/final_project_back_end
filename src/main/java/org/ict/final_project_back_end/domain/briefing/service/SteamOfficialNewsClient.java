package org.ict.final_project_back_end.domain.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SteamOfficialNewsClient {
  public record Source(String gid,String title,String content,String url,Instant publishedAt,String fingerprint) {
    public Source(String gid,String title,String content,String url,Instant publishedAt){this(gid,title,content,url,publishedAt,null);}
  }
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final ObjectMapper json=new ObjectMapper();
  private final String baseUrl;
  public SteamOfficialNewsClient(@Value("${briefing.steam-base-url:https://api.steampowered.com}") String baseUrl){this.baseUrl=baseUrl;}
  public List<Source> fetch(long appId) throws Exception {
    var uri=URI.create(baseUrl+"/ISteamNews/GetNewsForApp/v2/?appid="+appId+"&count=10&maxlength=0&feeds=steam_community_announcements&format=json");
    var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()!=200)throw new IllegalStateException("STEAM_HTTP_"+response.statusCode());
    if(response.body().length()>2_000_000)throw new IllegalStateException("STEAM_RESPONSE_TOO_LARGE");
    var app=json.readTree(response.body()).path("appnews");
    if(app.path("appid").asLong()!=appId || !app.path("newsitems").isArray())throw new IllegalStateException("STEAM_INVALID_RESPONSE");
    List<Source> results=new ArrayList<>();
    for(var n:app.path("newsitems")) {
      if(results.size()>=10)break;
      String gid=n.path("gid").asText(),url=n.path("url").asText();
      if(!"steam_community_announcements".equals(n.path("feedname").asText()) || !gid.matches("[0-9]{1,32}") || !BriefingContent.safeUrl(url))continue;
      String title=BriefingContent.plain(n.path("title").asText());
      String content=BriefingContent.plain(n.path("contents").asText());
      if(title.isBlank() || content.isBlank() || n.path("date").asLong()<=0)continue;
      String fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((n.path("title").asText()+"\u0000"+n.path("contents").asText()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      results.add(new Source(gid,title.substring(0,Math.min(1000,title.length())),content,url,Instant.ofEpochSecond(n.path("date").asLong()),fingerprint));
    }
    return List.copyOf(results);
  }
}
