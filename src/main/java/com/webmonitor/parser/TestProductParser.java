package com.webmonitor.parser;

import com.webmonitor.domain.Product.StockStatus;
import com.webmonitor.dto.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 테스트용 제품 파서
 * 실제 쇼핑몰 파싱 대신 간단한 HTML 구조를 시뮬레이션
 */
@Component
@Slf4j
public class TestProductParser implements ProductParser {

    @Override
    public ProductInfo parseProduct(Document document, String url) {
        log.debug("테스트 제품 파싱 시작: {}", url);

        try {
            // 상품명 추출
            String name = "테스트 상품";
            Element titleElement = document.selectFirst("h1.product-title, h1");
            if (titleElement != null) {
                name = titleElement.text().trim();
            }

            // 재고 상태 추출
            StockStatus stockStatus = StockStatus.UNKNOWN;
            Element stockElement = document.selectFirst(".stock-status, .availability");
            if (stockElement != null) {
                String stockText = stockElement.text().toLowerCase();
                if (stockText.contains("재고있음") || stockText.contains("in stock") ||
                    stockText.contains("구매가능") || stockText.contains("available")) {
                    stockStatus = StockStatus.IN_STOCK;
                } else if (stockText.contains("품절") || stockText.contains("out of stock") ||
                           stockText.contains("sold out") || stockText.contains("일시품절")) {
                    stockStatus = StockStatus.OUT_OF_STOCK;
                }
            }

            // 가격 추출
            BigDecimal price = null;
            Element priceElement = document.selectFirst(".price, .product-price");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    price = new BigDecimal(priceText);
                }
            }

            // 이미지 URL 추출
            String imageUrl = null;
            Element imageElement = document.selectFirst(".product-image img, .main-image img");
            if (imageElement != null) {
                imageUrl = imageElement.attr("src");
            }

            ProductInfo productInfo = ProductInfo.builder()
                    .name(name)
                    .stockStatus(stockStatus)
                    .price(price)
                    .imageUrl(imageUrl)
                    .shopName("테스트샵")
                    .build();

            log.debug("테스트 제품 파싱 완료: {}", productInfo);
            return productInfo;

        } catch (Exception e) {
            log.error("테스트 제품 파싱 중 오류 발생: {}", url, e);

            // 오류 발생 시 기본값 반환
            return ProductInfo.builder()
                    .name("파싱 실패")
                    .stockStatus(StockStatus.UNKNOWN)
                    .shopName("테스트샵")
                    .build();
        }
    }

    @Override
    public String getSupportedDomain() {
        return "test.com";
    }
}
