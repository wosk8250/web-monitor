package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Product;
import com.webmonitor.util.DiscordConstants;
import com.webmonitor.util.DiscordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordService {

    private final DiscordWebhookCaller discordWebhookCaller;

    /**
     * 디스코드로 알림 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param alert 전송할 알림 정보
     */
    public void sendAlert(String webhookUrl, Alert alert) {
        if (!isValidWebhook(webhookUrl)) return;
        log.info("디스코드 알림 전송 시작 - 알림 ID: {}, 메시지: {}", alert.getId(), alert.getMessage());

        try {
            // Discord Embed 형식으로 메시지 구성
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", alert.getMessage());
            embed.put("url", alert.getDetectedUrl());
            embed.put("color", DiscordConstants.EMBED_COLOR_BLUE);
            embed.put("timestamp", alert.getDetectedAt().toString());

            // 필드 추가
            Map<String, Object> field1 = new HashMap<>();
            field1.put("name", "사이트");
            field1.put("value", alert.getSite() != null ? alert.getSite().getName() : "제품");
            field1.put("inline", true);

            Map<String, Object> field2 = new HashMap<>();
            field2.put("name", "시간");
            field2.put("value", alert.getDetectedAt().format(DiscordConstants.DISPLAY_FORMATTER));
            field2.put("inline", true);

            embed.put("fields", List.of(field1, field2));

            // Footer 추가
            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "웹 모니터링 시스템");
            embed.put("footer", footer);

            // 최종 요청 바디 구성
            Map<String, Object> body = new HashMap<>();
            body.put("embeds", List.of(embed));

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = discordWebhookCaller.sendDiscordWebhookWithRetry(
                    webhookUrl, request, "알림 전송 (ID: " + alert.getId() + ")");

            log.info("디스코드 알림 전송 성공 - 알림 ID: {}, HTTP 상태: {}",
                    alert.getId(), response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 알림 전송 실패 - 알림 ID: {}, 메시지: {}, 에러: {}",
                    alert.getId(), alert.getMessage(), e.getMessage());
            throw e; // AlertService의 재시도 메커니즘에서 처리하도록 예외 전파
        } catch (Exception e) {
            log.error("디스코드 알림 전송 중 예상치 못한 오류 - 알림 ID: {}, 에러: {}",
                    alert.getId(), e.getMessage(), e);
            throw new RuntimeException("Discord 알림 전송 실패", e);
        }
    }

    /**
     * 제품 재입고 알림을 디스코드로 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param product 재입고된 제품 정보
     */
    public void sendProductRestockAlert(String webhookUrl, Product product) {
        if (!isValidWebhook(webhookUrl)) return;
        log.info("디스코드 제품 재입고 알림 전송 시작 - 제품 ID: {}, 이름: {}", product.getId(), product.getName());

        try {
            String priorityText = product.getPriority() == Product.Priority.URGENT ? "🔴 긴급" : "🟢 일반";
            String priceText = product.getCurrentPrice() != null ?
                    String.format("%,d원", product.getCurrentPrice().intValue()) : "정보 없음";

            List<Map<String, Object>> fields = List.of(
                    embedField("재고", DiscordConstants.formatStockStatus(product.getCurrentStatus()), true),
                    embedField("가격", priceText, true),
                    embedField("우선순위", priorityText, true)
            );
            Map<String, Object> embed = buildProductEmbed(
                    "🔔 " + product.getName() + " 재입고!", DiscordConstants.EMBED_COLOR_GREEN,
                    "웹 모니터링 시스템 - 제품 재입고 알림", product, fields);

            ResponseEntity<String> response = sendEmbed(webhookUrl, embed, "재입고 알림 (" + product.getName() + ")");
            log.info("디스코드 제품 재입고 알림 전송 성공 - 제품: {}, HTTP 상태: {}", product.getName(), response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 제품 재입고 알림 전송 실패 - 제품: {}, 에러: {}", product.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("디스코드 제품 재입고 알림 전송 중 예상치 못한 오류 - 제품: {}, 에러: {}", product.getName(), e.getMessage(), e);
            throw new RuntimeException("Discord 재입고 알림 전송 실패", e);
        }
    }

    /**
     * 제품 콘텐츠 변경 알림을 디스코드로 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param product 콘텐츠가 변경된 제품 정보
     */
    public void sendProductContentChangeAlert(String webhookUrl, Product product) {
        if (!isValidWebhook(webhookUrl)) return;
        log.info("디스코드 콘텐츠 변경 알림 전송 시작 - 제품 ID: {}, 이름: {}", product.getId(), product.getName());

        try {
            String priorityText = product.getPriority() == Product.Priority.URGENT ? "🔴 긴급" : "🟢 일반";

            List<Map<String, Object>> fields = List.of(
                    embedField("쇼핑몰", product.getShopName() != null ? product.getShopName() : "정보 없음", true),
                    embedField("우선순위", priorityText, true)
            );
            Map<String, Object> embed = buildProductEmbed(
                    "📄 " + product.getName() + " 콘텐츠 변경", DiscordConstants.EMBED_COLOR_ORANGE,
                    "웹 모니터링 시스템 - 콘텐츠 변경 알림", product, fields);

            ResponseEntity<String> response = sendEmbed(webhookUrl, embed, "콘텐츠 변경 알림 (" + product.getName() + ")");
            log.info("디스코드 콘텐츠 변경 알림 전송 성공 - 제품: {}, HTTP 상태: {}", product.getName(), response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 콘텐츠 변경 알림 전송 실패 - 제품: {}, 에러: {}", product.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("디스코드 콘텐츠 변경 알림 전송 중 예상치 못한 오류 - 제품: {}, 에러: {}", product.getName(), e.getMessage(), e);
            throw new RuntimeException("Discord 콘텐츠 변경 알림 전송 실패", e);
        }
    }

    // Finding #6: sendProductRestockAlert·sendProductContentChangeAlert 공통 embed 빌더 추출
    private Map<String, Object> buildProductEmbed(String title, int color, String footerText,
                                                   Product product, List<Map<String, Object>> fields) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", title);
        embed.put("url", product.getUrl());
        embed.put("color", color);
        embed.put("description", "━━━━━━━━━━━━━━━");
        embed.put("fields", fields);

        Map<String, Object> footer = new HashMap<>();
        footer.put("text", footerText);
        embed.put("footer", footer);

        if (product.getImageUrl() != null && !product.getImageUrl().trim().isEmpty()) {
            Map<String, Object> thumbnail = new HashMap<>();
            thumbnail.put("url", product.getImageUrl());
            embed.put("thumbnail", thumbnail);
        }
        return embed;
    }

    private Map<String, Object> embedField(String name, String value, boolean inline) {
        Map<String, Object> field = new HashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        return field;
    }

    private ResponseEntity<String> sendEmbed(String webhookUrl, Map<String, Object> embed, String context) {
        Map<String, Object> body = new HashMap<>();
        body.put("embeds", List.of(embed));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return discordWebhookCaller.sendDiscordWebhookWithRetry(webhookUrl, new HttpEntity<>(body, headers), context);
    }

    private boolean isValidWebhook(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) return false;
        if (!DiscordUtils.isValidDiscordWebhookUrl(webhookUrl)) {
            log.warn("잘못된 디스코드 웹훅 URL 형식입니다. URL: {}...", DiscordUtils.maskWebhookUrl(webhookUrl));
            return false;
        }
        return true;
    }

    /**
     * 간단한 텍스트 메시지를 디스코드로 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param message 전송할 메시지
     */
    public void sendSimpleMessage(String webhookUrl, String message) {
        if (!isValidWebhook(webhookUrl)) return;
        log.info("디스코드 간단 메시지 전송 시작");

        try {
            // 간단한 메시지 형식
            Map<String, Object> body = new HashMap<>();
            body.put("content", message);

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = discordWebhookCaller.sendDiscordWebhookWithRetry(
                    webhookUrl, request, "간단 메시지 전송");

            log.info("디스코드 간단 메시지 전송 성공 - HTTP 상태: {}",
                    response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 메시지 전송 실패 - 에러: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("디스코드 메시지 전송 중 예상치 못한 오류 - 에러: {}", e.getMessage(), e);
            throw new RuntimeException("Discord 메시지 전송 실패", e);
        }
    }

}
