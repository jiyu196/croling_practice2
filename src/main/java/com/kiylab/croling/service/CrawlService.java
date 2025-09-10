package com.kiylab.croling.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.lang.model.util.Elements;
import javax.swing.text.Document;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class CrawlService {
  // 이전에는 리스트 따로, 상세페이지 링크 따로해서 크롤링함.
  // 이제는 카테고리 리스트 들어갔다가, 상세로 알아서 넘어가는. 형식

  private final ProductCrawlRepository productCrawlRepository;
  private final AttachCrawlRepository attachCrawlRepository;
  private final CrolingUtil crolingUtil;
  private final TagRepository tagRepository;

// 밑에있는건 이전 상세페이지 크롤링하는 코드만
//  public void crawlAndSave(String url) throws Exception {
//    WebDriverManager.chromedriver().setup();
//    WebDriver driver = new ChromeDriver();
//    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
//    driver.manage().window().maximize();
//
//    try {
//      driver.get(url);
//      Thread.sleep(2000);
//
//      // 1. Product 크롤링
//      String name = crolingUtil.getName(driver);
//      String description = crolingUtil.getDescription(driver);
//      int price = crolingUtil.getPrice(driver);
//      String category = crolingUtil.getCategory(driver);
//      String modelName = crolingUtil.getModelName(driver);
//
//      ProductCrawl productCrawl = ProductCrawl.builder()
//              .name(name)
//              .description(description)
//              .price(price)
//              .productCategory(category)
//              .modelName(modelName)
//              .build();
//
//      productCrawlRepository.save(productCrawl);
//
//      // 2. 썸네일 이미지 저장
//      String thumbnailPath = crolingUtil.getThumbnail(driver);
//      if (thumbnailPath != null) {
//        String path = thumbnailPath.substring(0, thumbnailPath.lastIndexOf("/"));
//        String uuid = thumbnailPath.substring(thumbnailPath.lastIndexOf("/") + 1);
//
//        AttachCrawl attachCrawl = AttachCrawl.builder()
//                .uuid(uuid)
//                .path(path)
//                .isThumbnail(true)
//                .productCrawl(productCrawl)
//                .build();
//
//        attachCrawlRepository.save(attachCrawl);
//      }
//
//      // 3. 상세설명 이미지 저장
//      List<String> descImgs = crolingUtil.getDescriptionImageUrls(driver);
//
//      for (String fullPath : descImgs) {
//        String fullUrl = fullPath.substring(0, fullPath.lastIndexOf("/"));
//        String uuid = fullPath.substring(fullPath.lastIndexOf("/") + 1);
//
//
//        String[] parts = fullUrl.split(".amazonaws.com/");
//        String path = parts.length > 1 ? parts[1] : "";
//
//        AttachCrawl descAttach = AttachCrawl.builder()
//                .uuid(uuid)
//                .path(path)
//                .isThumbnail(false)   // 상세설명 이미지니까 false
//                .productCrawl(productCrawl)
//                .build();
//
//        attachCrawlRepository.save(descAttach);
//      }
//
//      // 4. 태그 저장
//      // 카테고리(리스트) 페이지에서 뽑아온 태그를 상세페이지 모델명과 매칭
//      Map<String, Map<String, String>> tags = CrolingUtil.getTag(driver);
//      Map<String, String> currentSpecs = tags.get(modelName);
//
//      if (currentSpecs != null && !currentSpecs.isEmpty()) {
//        currentSpecs.forEach((tagName, tagValue) -> {
//          // value가 여러 개면 쉼표 기준으로 분리 저장
//          if (tagValue.contains(",")) {
//            for (String v : tagValue.split(",")) {
//              tagRepository.save(
//                      Tag.builder()
//                              .product(productCrawl)
//                              .tagName(tagName)
//                              .tagValue(v.trim())
//                              .build()
//              );
//            }
//          } else {
//            tagRepository.save(
//                    Tag.builder()
//                            .product(productCrawl)
//                            .tagName(tagName)
//                            .tagValue(tagValue.trim())
//                            .build()
//            );
//          }
//        });
//      }
//      System.out.println("DB 저장 완료: " + productCrawl.getName());
//
//    } finally {
//      driver.quit();
//    }
//  }
  /**
   * 카테고리 URL 크롤링
   * - 카테고리 페이지에서 제품 리스트 li 요소 읽음
   * - 각 li의 data-ec-product(JSON) 파싱
   * - model_id → model_sku 순으로 상세페이지 URL 조립
   * - 상세페이지 들어가서 상품 저장
   */
  public void crawlCategory(String categoryUrl) throws Exception {
    WebDriverManager.chromedriver().setup();
    WebDriver driver = new ChromeDriver();
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    driver.manage().window().maximize();

    try {
      driver.get(categoryUrl);
      Thread.sleep(2000);

      // 1. 태그 맵 수집 (리스트에서만 가능)
      Map<String, Map<String, String>> tagMap = CrolingUtil.getTag(driver);

      // 2. 제품 리스트 li 요소 수집
      List<WebElement> products = driver.findElements(By.cssSelector("ul[role=list] > li"));

      ObjectMapper mapper = new ObjectMapper();

      for (WebElement product : products) {
        String rawJson = product.getAttribute("data-ec-product");
        if (rawJson == null || rawJson.isBlank()) continue;

        JsonNode json = mapper.readTree(rawJson);

        // model_sku (예: HW500DA5.AKOR)
        String modelSku = json.has("model_sku") ? json.get("model_sku").asText() : null;
        if (modelSku == null || modelSku.isBlank()) continue;

        // 카테고리 URL에서 "/category" 제거 → 상세페이지 패턴 맞추기
        String baseCategory = categoryUrl.replace("/category", "");

        // 상세페이지 URL 조립 (소문자 + 점 → 대시 변환)
        String detailUrl = baseCategory + "/" + modelSku.toLowerCase().replace(".", "-");

        // 상세페이지 크롤링 실행
        crawlAndSaveWithTags(detailUrl, tagMap);
      }

    } finally {
      driver.quit();
    }
  }

  /**
   * 상세페이지 하나 크롤링 + 저장
   */
  private void crawlAndSaveWithTags(String url, Map<String, Map<String, String>> tagMap) throws Exception {
    WebDriver driver = new ChromeDriver();
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    driver.manage().window().maximize();

    try {
      driver.get(url);
      Thread.sleep(2000);

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

      // 썸네일 저장
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

      // 상세설명 이미지 저장
      List<String> descImgs = crolingUtil.getDescriptionImageUrls(driver);
      for (String fullPath : descImgs) {
        String fullUrl = fullPath.substring(0, fullPath.lastIndexOf("/"));
        String uuid = fullPath.substring(fullPath.lastIndexOf("/") + 1);

        String[] parts = fullUrl.split(".amazonaws.com/");
        String path = parts.length > 1 ? parts[1] : "";

        AttachCrawl descAttach = AttachCrawl.builder()
                .uuid(uuid)
                .path(path)
                .isThumbnail(false)
                .productCrawl(productCrawl)
                .build();

        attachCrawlRepository.save(descAttach);
      }

      // 태그 저장
      Map<String, String> currentSpecs = tagMap.get(modelName);
      if (currentSpecs != null && !currentSpecs.isEmpty()) {
        currentSpecs.forEach((tagName, tagValue) -> {
          if (tagValue.contains(",")) {
            for (String v : tagValue.split(",")) {
              tagRepository.save(
                      Tag.builder()
                              .product(productCrawl)
                              .tagName(tagName)
                              .tagValue(v.trim())
                              .build()
              );
            }
          } else {
            tagRepository.save(
                    Tag.builder()
                            .product(productCrawl)
                            .tagName(tagName)
                            .tagValue(tagValue.trim())
                            .build()
            );
          }
        });
      }

      System.out.println("DB 저장 완료: " + productCrawl.getName());

    } finally {
      driver.quit();
    }
  }
}