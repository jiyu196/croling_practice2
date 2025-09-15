package com.kiylab.croling.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tbl_product_option")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOption {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long optionId;

  // N:1 관계 매핑
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductCrawl productCrawl;

  // PURCHASE, SUBSCRIBE (구매, 구독)
  private String optionType;

  // 정가/ 월 요금
  @Column(nullable = false)
  private Integer price;

  // 구독 기간 (개월 단위)
  private Integer period;

  // 보증금(위약금)
  private BigDecimal deposit;

  // ACTIVE / DISABLED
  private String status;
}

