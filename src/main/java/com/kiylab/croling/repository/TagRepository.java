package com.kiylab.croling.repository;

import com.kiylab.croling.entity.ProductCrawl;
import com.kiylab.croling.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

}
