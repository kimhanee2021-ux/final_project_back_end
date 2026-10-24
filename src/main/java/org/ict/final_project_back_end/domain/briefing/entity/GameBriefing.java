package org.ict.final_project_back_end.domain.briefing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity @Table(name="ai_game_briefings") @Getter @NoArgsConstructor
public class GameBriefing {
  @Id private Long appId;
  @Column(nullable=false,length=300) private String gameName;
  @Column(nullable=false,length=64) private String sourceHash;
  @Column(nullable=false,columnDefinition="TEXT") private String resultJson;
  @Column(nullable=false) private Instant analyzedAt;
  @Column(nullable=false) private Instant checkedAt;
  @Column(nullable=false) private int sourceCount;
  public GameBriefing(Long appId,String gameName,String hash,String result,Instant now,int sourceCount) {
    this.appId=appId;this.gameName=gameName;this.sourceHash=hash;this.resultJson=result;this.analyzedAt=now;this.checkedAt=now;this.sourceCount=sourceCount;
  }
  public void checked(Instant now){this.checkedAt=now;}
}
