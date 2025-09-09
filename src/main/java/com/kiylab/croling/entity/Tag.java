package com.kiylab.croling.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_tag")  // ✅ 테이블명만 바꿈
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long tagId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  ProductCrawl product;
  @Column(nullable = false)
  String tagName;
  @Column(nullable = false)
  String tagValue;
}
