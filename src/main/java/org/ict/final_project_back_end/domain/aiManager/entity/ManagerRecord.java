package org.ict.final_project_back_end.domain.aiManager.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="ai_manager_records",indexes=@Index(name="idx_manager_user_kind",columnList="user_id,kind"))
public class ManagerRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="user_id",nullable=false) public Long userId;
 @Column(nullable=false,length=20) public String kind;
 @Column(nullable=false,columnDefinition="LONGTEXT") public String payload;
 @Column(nullable=false) public Instant createdAt;
 @Version public Long version;
 protected ManagerRecord(){}
 public ManagerRecord(Long userId,String kind,String payload){this.userId=userId;this.kind=kind;this.payload=payload;this.createdAt=Instant.now();}
}
