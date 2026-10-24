package org.ict.final_project_back_end.domain.briefing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity @Table(name="ai_official_news", uniqueConstraints=@UniqueConstraint(columnNames={"app_id","gid"}))
@Getter @NoArgsConstructor
public class OfficialNews {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="app_id",nullable=false) private Long appId;
  @Column(nullable=false,length=32) private String gid;
  @Column(nullable=false,length=300) private String gameName;
  @Column(nullable=false,length=1000) private String title;
  @Column(nullable=false,columnDefinition="TEXT") private String content;
  @Column(length=1000) private String koreanTitle;
  @Column(columnDefinition="TEXT") private String koreanSummary;
  @Column(length=64) private String translationHash;
  @Column(length=64) private String sourceFingerprint;
  @Column(nullable=false,length=2000) private String url;
  @Column(nullable=false) private Instant publishedAt;
  @Column(nullable=false) private Instant fetchedAt;
  public OfficialNews(Long appId,String gameName,String gid) {this.appId=appId;this.gameName=gameName;this.gid=gid;}
  public void refresh(String title,String content,String url,Instant publishedAt,Instant now,String fingerprint) {
    if(fingerprint!=null && fingerprint.equals(sourceFingerprint)){fetchedAt=now;return;}
    update(title,content,url,publishedAt,now);sourceFingerprint=fingerprint;
  }
  public void translate(String title,String summary) {
    koreanTitle=title;koreanSummary=summary;
    try {translationHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((this.title+"\u0000"+content).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException(e);}
  }
  public void update(String title,String content,String url,Instant publishedAt,Instant now) {
    if(!java.util.Objects.equals(this.title,title) || !java.util.Objects.equals(this.content,content)) {
      koreanTitle=null;koreanSummary=null;translationHash=null;
    }
    this.title=title;this.content=content;this.url=url;this.publishedAt=publishedAt;this.fetchedAt=now;
  }
}
