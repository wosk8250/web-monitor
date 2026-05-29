package com.webmonitor.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * 사이트 URL에 따라 적절한 파서를 반환하는 Factory
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SiteParserFactory {

    private final List<SiteParser> parsers;

    /**
     * URL에 맞는 파서 반환
     *
     * @param url 사이트 URL
     * @return 해당 URL에 맞는 파서 (없으면 null)
     */
    public SiteParser getParser(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host == null) {
                log.warn("URL 파싱 실패: {}", url);
                return null;
            }

            // 등록된 파서 중에서 도메인이 일치하는 파서 찾기
            for (SiteParser parser : parsers) {
                String supportedDomain = parser.getSupportedDomain();

                if (host.equals(supportedDomain) || host.endsWith("." + supportedDomain)) {
                    log.debug("파서 찾음: {} -> {}", url, parser.getClass().getSimpleName());
                    return parser;
                }
            }

            log.debug("지원되지 않는 도메인: {} (사용 가능한 파서: {})", host, parsers.size());
            return null;

        } catch (URISyntaxException e) {
            log.error("URL 파싱 중 오류: {}", url, e);
            return null;
        }
    }

    /**
     * 특정 URL에 대한 파서가 존재하는지 확인
     *
     * @param url 사이트 URL
     * @return 파서 존재 여부
     */
    public boolean hasParser(String url) {
        return getParser(url) != null;
    }
}
