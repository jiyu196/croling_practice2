package com.kiylab.croling.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CrawlServiceTest {

  @Autowired
  private CrawlService crawlService;


    @Test
    public void crawlingTest() throws Exception {
      crawlService.crawlAndSave("https://www.lge.co.kr/water-purifiers/wd323acb");


    }

}
