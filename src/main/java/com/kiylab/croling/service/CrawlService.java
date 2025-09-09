package com.kiylab.croling.service;

import com.kiylab.croling.entity.AttachCrawl;
import com.kiylab.croling.entity.ProductCrawl;
import com.kiylab.croling.entity.Tag;
import com.kiylab.croling.repository.AttachCrawlRepository;
import com.kiylab.croling.repository.ProductCrawlRepository;
import com.kiylab.croling.repository.TagRepository;
import com.kiylab.croling.util.CrolingUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Service;

import javax.lang.model.util.Elements;
import javax.swing.text.Document;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CrawlService {


  private final ProductCrawlRepository productCrawlRepository;
  private final AttachCrawlRepository attachCrawlRepository;
  private final CrolingUtil crolingUtil;
  private final TagRepository tagRepository;


  public void crawlAndSave(String url) throws Exception {
    WebDriverManager.chromedriver().setup();
    WebDriver driver = new ChromeDriver();
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    driver.manage().window().maximize();

    try {
      driver.get(url);
      Thread.sleep(2000);

      // 1. Product 크롤링
      String name = crolingUtil.getName(driver);
      String description = crolingUtil.getDescription(driver);
      int price = crolingUtil.getPrice(driver);
      String category = crolingUtil.getCategory(driver);
      String modelName = crolingUtil.getModelName(driver);

      ProductCrawl productCrawl = ProductCrawl.builder()
              .name(name)
              .description(description)
              .price(price)
              .productCategory(category)
              .modelName(modelName)
              .build();

      productCrawlRepository.save(productCrawl);

      // 2. 썸네일 이미지 저장
      String thumbnailPath = crolingUtil.getThumbnail(driver);
      if (thumbnailPath != null) {
        String path = thumbnailPath.substring(0, thumbnailPath.lastIndexOf("/"));
        String uuid = thumbnailPath.substring(thumbnailPath.lastIndexOf("/") + 1);

        AttachCrawl attachCrawl = AttachCrawl.builder()
                .uuid(uuid)
                .path(path)
                .isThumbnail(true)
                .productCrawl(productCrawl)
                .build();

        attachCrawlRepository.save(attachCrawl);
      }

      // 3. 상세설명 이미지 저장
      List<String> descImgs = crolingUtil.getDescriptionImageUrls(driver);

      for (String fullPath : descImgs) {
        String fullUrl = fullPath.substring(0, fullPath.lastIndexOf("/"));
        String uuid = fullPath.substring(fullPath.lastIndexOf("/") + 1);


        String[] parts = fullUrl.split(".amazonaws.com/");
        String path = parts.length > 1 ? parts[1] : "";

        AttachCrawl descAttach = AttachCrawl.builder()
                .uuid(uuid)
                .path(path)
                .isThumbnail(false)   // 상세설명 이미지니까 false
                .productCrawl(productCrawl)
                .build();

        attachCrawlRepository.save(descAttach);
      }

//      // 4. 태그 저장 (util에서 가져오기)
//      Map<String, Map<String, String>> tags = CrolingUtil.getTag(driver);
//      Map<String, String> currentSpecs = tags.get(modelName);
//
//      if (currentSpecs != null) {
//        currentSpecs.forEach((tagName, tagValue) -> {
//          tagRepository.save(
//                  Tag.builder()
//                          .product(productCrawl) // FK 매핑
//                          .tagName(tagName)
//                          .tagValue(tagValue)
//                          .build()
//          );
//        });
//      }
//      System.out.println("DB 저장 완료: " + productCrawl.getName());

    } finally {
      driver.quit();
    }
  }

}
