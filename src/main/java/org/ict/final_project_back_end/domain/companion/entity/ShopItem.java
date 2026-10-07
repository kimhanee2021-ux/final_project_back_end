package org.ict.final_project_back_end.domain.companion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 방 꾸미기 상점 카탈로그
@Entity
@Table(name = "shop_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ShopItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_name", nullable = false, length = 100)
  private String itemName;

  @Column(name = "item_type", nullable = false, length = 30)
  private String itemType;

  @Column(name = "point_cost", nullable = false)
  private Integer pointCost;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;
}
