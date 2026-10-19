package org.ict.final_project_back_end.domain.aiPc.entity;
import jakarta.persistence.*;
@Entity @Table(name="pc_part_price_snapshots",indexes=@Index(name="idx_pc_part_price",columnList="productCode,id"))
public class PcPartPriceSnapshot {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private long productCode;
 @Column(length=30,nullable=false) private String sourceKey="DANAWA";
 private Long priceKrw;
 @Column(length=30,nullable=false) private String status;
 @Column(length=300,nullable=false) private String note;
 @Column(length=50,nullable=false) private String checkedAt;
 protected PcPartPriceSnapshot(){}
 public PcPartPriceSnapshot(long code,Long price,String status,String note,String at){this.productCode=code;this.priceKrw=price;this.status=status;this.note=note;this.checkedAt=at;}
 public PcPartPriceSnapshot(long code,String source,Long price,String status,String note,String at){this(code,price,status,note,at);this.sourceKey=source;}
 public Long price(){return priceKrw;} public String status(){return status;} public String note(){return note;} public String at(){return checkedAt;}
}
