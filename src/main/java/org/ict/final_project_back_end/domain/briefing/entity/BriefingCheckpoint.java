package org.ict.final_project_back_end.domain.briefing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.*;

@Entity @Table(name="ai_briefing_checkpoint") @Getter @NoArgsConstructor
public class BriefingCheckpoint {
  @Id private Long id;
  private long cursorAppId;
  private int requestsToday;
  private LocalDate quotaDay;
  private Instant lastAttemptAt;
  private Instant lastSuccessAt;
  @Column(length=100) private String status;
  public BriefingCheckpoint(long id) {this.id=id;this.status="READY";}
  public void attempted(Instant now) {lastAttemptAt=now;}
  public void advance(long appId, Instant now) {cursorAppId=appId;lastSuccessAt=now;status="OK";}
  public void rewind() {cursorAppId=0;}
  public void skip(long appId) {cursorAppId=appId;}
  public boolean reserve(LocalDate day,int max) {
    if(!day.equals(quotaDay)){quotaDay=day;requestsToday=0;}
    if(requestsToday>=max)return false;
    requestsToday++;return true;
  }
  public void failed(String status) {this.status=status;}
}
