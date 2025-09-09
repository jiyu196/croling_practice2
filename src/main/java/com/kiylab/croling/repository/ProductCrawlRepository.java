package com.kiylab.croling.repository;

import com.kiylab.croling.entity.ProductCrawl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCrawlRepository extends JpaRepository<ProductCrawl, Long> {

  Optional<ProductCrawl> findByModelName(String modelName);
}

