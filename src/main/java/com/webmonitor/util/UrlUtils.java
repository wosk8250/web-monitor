package com.webmonitor.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * URL 검증 유틸리티 클래스
 * SSRF (Server-Side Request Forgery) 공격 방어
 */
@UtilityClass
@Slf4j
public class UrlUtils {

    /**
     * URL 보안 검증 (SSRF 공격 방어)
     *
     * @param urlString 검증할 URL
     * @throws IllegalArgumentException URL이 보안 정책을 위반하는 경우
     */
    public static void validateUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("URL은 null이거나 빈 문자열일 수 없습니다");
        }

        try {
            URL url = new URL(urlString);

            // 1. 프로토콜 제한 (HTTP/HTTPS만 허용)
            if (!url.getProtocol().matches("https?")) {
                log.warn("허용되지 않은 프로토콜 감지: {}", url.getProtocol());
                throw new IllegalArgumentException("HTTP 또는 HTTPS 프로토콜만 허용됩니다: " + url.getProtocol());
            }

            // 2. 내부 IP 차단 (SSRF 방어)
            InetAddress address = InetAddress.getByName(url.getHost());

            if (address.isLoopbackAddress()) {
                log.warn("Loopback 주소 접근 시도 차단: {}", address.getHostAddress());
                throw new IllegalArgumentException("Loopback 주소는 허용되지 않습니다: " + address.getHostAddress());
            }

            if (address.isLinkLocalAddress()) {
                log.warn("Link-local 주소 접근 시도 차단: {}", address.getHostAddress());
                throw new IllegalArgumentException("Link-local 주소는 허용되지 않습니다: " + address.getHostAddress());
            }

            if (address.isSiteLocalAddress()) {
                log.warn("Site-local (내부 IP) 주소 접근 시도 차단: {}", address.getHostAddress());
                throw new IllegalArgumentException("내부 IP 주소는 허용되지 않습니다: " + address.getHostAddress());
            }

            // 3. 포트 제한 (80, 443만 허용)
            int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
            if (port != 80 && port != 443) {
                log.warn("허용되지 않은 포트 접근 시도: {}", port);
                throw new IllegalArgumentException("80 또는 443 포트만 허용됩니다: " + port);
            }

            log.debug("URL 검증 통과: {}", urlString);

        } catch (MalformedURLException e) {
            log.error("잘못된 URL 형식: {}", urlString);
            throw new IllegalArgumentException("유효하지 않은 URL 형식입니다: " + urlString, e);
        } catch (UnknownHostException e) {
            log.error("호스트를 찾을 수 없음: {}", urlString);
            throw new IllegalArgumentException("호스트를 찾을 수 없습니다: " + urlString, e);
        }
    }

    /**
     * URL이 유효한지 검증 (예외 대신 boolean 반환)
     *
     * @param urlString 검증할 URL
     * @return 유효하면 true, 아니면 false
     */
    public static boolean isValidUrl(String urlString) {
        try {
            validateUrl(urlString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
