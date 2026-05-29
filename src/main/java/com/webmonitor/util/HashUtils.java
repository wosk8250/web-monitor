package com.webmonitor.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 해시 생성 유틸리티 클래스
 * SHA-256 해시 생성 기능 제공
 */
@UtilityClass
@Slf4j
public class HashUtils {

    /**
     * SHA-256 해시 생성
     *
     * @param content 해시를 생성할 문자열
     * @return SHA-256 해시 (16진수 문자열), 실패 시 빈 문자열
     */
    public static String sha256(String content) {
        if (content == null) {
            log.warn("해시 생성 시도: content가 null입니다");
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 해시 알고리즘을 찾을 수 없습니다: {}", e.getMessage());
            return "";
        }
    }
}
