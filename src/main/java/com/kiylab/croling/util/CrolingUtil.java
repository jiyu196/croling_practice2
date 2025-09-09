package com.kiylab.croling.util;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;


@Component
@RequiredArgsConstructor
public class CrolingUtil {
  private final S3Upload s3Upload;


  public void printPageHtml(String url) throws Exception {
    WebDriverManager.chromedriver().setup();
    WebDriver driver = new ChromeDriver();
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    driver.manage().window().maximize();

    try {
      driver.get(url);
      Thread.sleep(2000);

//      String name = getName(driver);
      String description = getDescription(driver);
//      int price = getPrice(driver);
//      String category = getCategory(driver);
//      String thumbnail = getThumbnail(driver);
//      String modelName = getModelName(driver);


      // 최종 결과 출력
      System.out.println("===== data-* 속성 제거된 첫 번째 iw_placeholder 블록 =====");
      System.out.println(description);
    } finally {
      driver.quit();
    }
  }

  private static void cleanDataAttributes(WebDriver driver, WebElement element) {
    JavascriptExecutor js = (JavascriptExecutor) driver;

    js.executeScript(
            "var el = arguments[0]; " +
                    "var attrs = el.attributes; " +
                    "for (var i = attrs.length - 1; i >= 0; i--) { " +
                    "  if (attrs[i].name.startsWith('data-')) { " +
                    "    el.removeAttribute(attrs[i].name); " +
                    "  } " +
                    "}", element
    );
  }

  public String getName(WebDriver driver) {
    try {
      WebElement nameEl = driver.findElement(By.cssSelector("h2.name"));
      String fullText = nameEl.getText();
      String name = fullText.split("\n")[0].trim();
      return replaceBrandName(name); // ✅ 치환 적용
    } catch (Exception e) {
      return "상품명 없음";
    }
  }

  public String getDescription(WebDriver driver) throws Exception {
    WebElement firstBlock = driver.findElement(By.className("iw_placeholder"));

    List<WebElement> allElements = firstBlock.findElements(By.xpath(".//*"));
    for (WebElement el : allElements) {
      cleanDataAttributes(driver, el);
    }

    String html = firstBlock.getDomProperty("outerHTML");
    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);

    // ✅ 불필요한 wrapper 요소 제거
    doc.select(".banner-wrap, .img-wrap, .img-alt, .mo, .mo-only, button, .bullet-list, .animation-area").remove();

    // ✅ a 태그 제거 (안에 있는 텍스트나 이미지만 살려서 대체)
    for (org.jsoup.nodes.Element a : doc.select("a")) {
      a.unwrap(); // <a>만 제거, 내부 내용은 유지
    }


    // ✅ 1. <img src="..."> 처리 + alt 분리
    for (org.jsoup.nodes.Element img : doc.select("img")) {
      String src = img.attr("src");

      // src가 없으면 data-*에서 대체 (이미 data는 지웠을 가능성 있으니 보강용)
      if (src == null || src.isBlank()) continue;

      if (!src.startsWith("http")) {
        src = "https://www.lge.co.kr" + src;
      }

      String s3Url = s3Upload.getFullUrl(s3Upload.upload(src));

      // alt 텍스트 분리
      String altText = img.hasAttr("alt") ? img.attr("alt") : null;

      // 새로운 img 태그
      org.jsoup.nodes.Element newImg = new org.jsoup.nodes.Element("img")
              .attr("src", s3Url)
              .attr("style", "width:100%; height:auto; display:block;");

      // alt 텍스트는 <p>로 별도 생성
      if (altText != null && !altText.isBlank()) {
        org.jsoup.nodes.Element altPara = new org.jsoup.nodes.Element("p")
                .attr("class", "img-alt")
                .text(altText);

        // img + alt를 감싸는 wrapper div
        org.jsoup.nodes.Element wrapper = new org.jsoup.nodes.Element("div")
                .appendChild(newImg)
                .appendChild(altPara);

        img.replaceWith(wrapper);
      } else {
        img.replaceWith(newImg);
      }
    }

    // ✅ 2. style="background:url(...)" 처리 → img + alt 분리
    for (org.jsoup.nodes.Element el : doc.select("[style]")) {
      String style = el.attr("style");

      // 모바일 전용 요소 제거
      if (el.hasClass("mo") || el.hasClass("mo-only")) {
        el.remove();
        continue;
      }

      // 불필요한 클래스 제거
      if (el.hasClass("banner-wrap") || el.hasClass("img-wrap") || el.hasClass("img-alt")) {
        el.remove();
        continue;
      }

      if (style != null && style.contains("url(")) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("url\\(([^)]*)\\)")
                .matcher(style);

        if (matcher.find()) {
          String url = matcher.group(1)
                  .replace("\"", "")
                  .replace("'", "")
                  .trim();

          if (!url.startsWith("http")) {
            url = "https://www.lge.co.kr" + url;
          }

          String s3Url = s3Upload.getFullUrl(s3Upload.upload(url));

          // 새 img 태그 생성
          org.jsoup.nodes.Element newImg = new org.jsoup.nodes.Element("img")
                  .attr("src", s3Url)
                  .attr("style", "width:100%; height:auto; display:block;");

          // alt 속성 있으면 분리
          String altText = el.hasAttr("alt") ? el.attr("alt") : null;
          if (altText != null && !altText.isBlank()) {
            org.jsoup.nodes.Element altPara = new org.jsoup.nodes.Element("p")
                    .attr("class", "img-alt")
                    .text(altText);

            org.jsoup.nodes.Element wrapper = new org.jsoup.nodes.Element("div")
                    .appendChild(newImg)
                    .appendChild(altPara);

            el.replaceWith(wrapper);
          } else {
            el.replaceWith(newImg);
          }
        }
      }
    }

// ✅ src 없는 <img> 제거
    for (org.jsoup.nodes.Element img : doc.select("img")) {
      String src = img.attr("src");
      if (src == null || src.isBlank()) {
        img.remove();
      }
    }


    String resultHtml = doc.body().html();
    return replaceBrandName(resultHtml); // ✅ 치환 적용
  }

  public List<String> getDescriptionImageUrls(WebDriver driver) throws Exception {
    WebElement firstBlock = driver.findElement(By.className("iw_placeholder"));
    String html = firstBlock.getDomProperty("outerHTML");

    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);

    List<String> imageUrls = new ArrayList<>();

    for (org.jsoup.nodes.Element img : doc.select("img")) {
      String src = img.attr("src");
      if (src != null && !src.isBlank()) {
        if (!src.startsWith("http")) {
          src = "https://www.lge.co.kr" + src;
        }
        String s3Url = s3Upload.getFullUrl(s3Upload.upload(src));
        imageUrls.add(s3Url);
      }
    }
    return imageUrls;
  }

  public int getPrice(WebDriver driver) {
    try {
      WebElement priceEl = driver.findElement(By.cssSelector("dl.price-info.total-payment span.price em"));
      String rawText = priceEl.getText();
      String numeric = rawText.replaceAll("[^0-9]", "");
      return Integer.parseInt(numeric);
    } catch (Exception e) {
      return 0;
    }
  }

  public String getCategory(WebDriver driver) {
    try {
      List<WebElement> items = driver.findElements(
              By.cssSelector("ul[itemtype='http://schema.org/BreadcrumbList'] li"));

      if (items.size() < 2) {
        return "카테고리 없음";
      }

      WebElement target = items.get(items.size() - 2);
      WebElement textEl;
      try {
        textEl = target.findElement(By.tagName("a"));
      } catch (NoSuchElementException e) {
        textEl = target.findElement(By.tagName("span"));
      }

      return replaceBrandName(textEl.getText().trim()); // ✅ 치환 적용
    } catch (Exception e) {
      return "카테고리 없음";
    }
  }

  public String getThumbnail(WebDriver driver) {
    try {
      WebElement img = driver.findElement(By.cssSelector("img#base_detail_target"));
      String baseUrl = "https://www.lge.co.kr/";
      String imgUrl = img.getDomAttribute("src");
      return s3Upload.upload(baseUrl + imgUrl);
    } catch (Exception e) {
      return null;
    }
  }

  public String getModelName(WebDriver driver) {
    try {
      WebElement button = driver.findElement(By.cssSelector("button.sku.copy"));
      String fullText = button.getText().trim();
      WebElement span = button.findElement(By.cssSelector("span.blind"));
      String blindText = span.getText().trim();
      String model = fullText.replace(blindText, "").trim();
      return replaceBrandName(model); // ✅ 치환 적용
    } catch (Exception e) {
      return "모델명 없음";
    }
  }

  // LG → SAYREN 치환
  private String replaceBrandName(String text) {
    if (text == null) return null;
    return text.replaceAll("(?i)lg", "SAYREN");
  }
}