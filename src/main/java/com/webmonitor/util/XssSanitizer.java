package com.webmonitor.util;

import org.springframework.web.util.HtmlUtils;

/**
 * XSS 방어 유틸리티
 * 사용자 입력값에서 HTML 태그를 이스케이프하여 XSS 공격 방지
 */
public class XssSanitizer {

    /**
     * HTML 태그를 이스케이프 처리
     * 예: &lt;script&gt; -&gt; &amp;lt;script&amp;gt;
     *
     * @param input 사용자 입력 문자열
     * @return HTML 이스케이프된 안전한 문자열
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input);
    }
}
