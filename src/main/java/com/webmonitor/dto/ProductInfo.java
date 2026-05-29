package com.webmonitor.dto;

import com.webmonitor.domain.Product.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 파싱된 제품 정보를 담는 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {

    private String name;           // 상품명
    private StockStatus stockStatus; // 재고 상태
    private BigDecimal price;      // 가격
    private String imageUrl;       // 이미지 URL
    private String shopName;       // 쇼핑몰 이름
}
