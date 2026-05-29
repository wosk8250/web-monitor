package com.webmonitor.parser;

import com.webmonitor.dto.ProductInfo;
import org.jsoup.nodes.Document;

/**
 * 제품 정보 파싱 인터페이스
 */
public interface ProductParser {

    /**
     * HTML 문서에서 제품 정보를 파싱
     *
     * @param document 파싱할 HTML 문서
     * @param url      제품 URL
     * @return 파싱된 제품 정보
     */
    ProductInfo parseProduct(Document document, String url);

    /**
     * 이 파서가 지원하는 도메인 반환
     *
     * @return 지원하는 도메인 (예: "example.com")
     */
    String getSupportedDomain();
}
