package com.kiylab.croling.repository;

import com.kiylab.croling.entity.ProductCrawl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCrawlRepository extends JpaRepository<ProductCrawl, Long> {
  }

