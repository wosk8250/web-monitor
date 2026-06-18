package com.webmonitor.service;

import com.webmonitor.config.MonitoringProperties;
import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Product.StockStatus;
import com.webmonitor.dto.ProductInfo;
import com.webmonitor.exception.resource.ProductNotFoundException;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMonitorTransactionHandler {

    private final ProductRepository productRepository;
    private final AlertService alertService;
    private final SseService sseService;
    private final MonitoringProperties monitoringProperties;
    private final SettingService settingService;
    private final DiscordService discordService;

    @Transactional
    public void processParserBasedMonitoring(Long productId, ProductInfo info) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setPreviousStatus(product.getCurrentStatus());
        product.setPreviousPrice(product.getCurrentPrice());
        product.setCurrentStatus(info.getStockStatus());
        product.setCurrentPrice(info.getPrice());
        product.setImageUrl(info.getImageUrl());
        product.setShopName(info.getShopName());

        if (product.getNotifyOnRestock() &&
                product.getPreviousStatus() == StockStatus.OUT_OF_STOCK &&
                product.getCurrentStatus() == StockStatus.IN_STOCK) {
            log.info("재입고 감지: {}", product.getName());
            if (canSendRestockAlert(product)) {
                createRestockAlert(product);
                product.setLastRestockAlertAt(LocalDateTime.now());
            } else {
                log.info("재입고 알림 스킵 (최근 1시간 내 발송됨): {}", product.getName());
            }
        }

        product.setConsecutiveFailures(0);
        product.setLastCheckedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("[{}] 제품 모니터링 완료: {} - 상태: {}",
                Thread.currentThread().getName(), product.getName(), product.getCurrentStatus());
    }

    @Transactional
    public void monitorByContentHash(Long productId, Document document) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        String content;
        if (product.getContentSelector() != null && !product.getContentSelector().trim().isEmpty()) {
            Element element = document.selectFirst(product.getContentSelector());
            if (element != null) {
                content = element.text();
            } else {
                log.warn("셀렉터로 요소를 찾을 수 없음: {}", product.getContentSelector());
                content = document.body().text();
            }
        } else {
            content = document.body().text();
        }

        String currentHash = generateHash(content);
        if (currentHash == null) {
            log.error("해시 생성 실패로 모니터링 스킵: {}", product.getName());
            product.setLastCheckedAt(LocalDateTime.now());
            productRepository.save(product);
            return;
        }

        String previousHash = product.getLastContentHash();
        boolean isFirstCheck = (previousHash == null);
        boolean contentChanged = !isFirstCheck && !currentHash.equals(previousHash);

        if (contentChanged && product.getNotifyOnContentChange()) {
            log.info("콘텐츠 변경 감지: {}", product.getName());
            if (canSendContentChangeAlert(product)) {
                createContentChangeAlert(product);
                product.setLastContentChangeAlertAt(LocalDateTime.now());
            } else {
                log.info("변경 감지 알림 스킵 (최근 1시간 내 발송됨): {}", product.getName());
            }
        }

        product.setLastContentHash(currentHash);
        product.setConsecutiveFailures(0);
        product.setLastCheckedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("[{}] 콘텐츠 해시 모니터링 완료: {}",
                Thread.currentThread().getName(), product.getName());
    }

    @Transactional
    public void handleFailure(Long productId, Exception exception) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setConsecutiveFailures(product.getConsecutiveFailures() + 1);
        product.setLastFailureAt(LocalDateTime.now());
        product.setLastCheckedAt(LocalDateTime.now());
        productRepository.save(product);

        log.warn("제품 모니터링 실패 (연속 {}회): {}",
                product.getConsecutiveFailures(), product.getName());

        int alertThreshold = monitoringProperties.getFailure().getAlertThreshold();
        if (product.getConsecutiveFailures() >= alertThreshold) {
            final Product p = product;
            final Exception ex = exception;
            // Finding #6: DB 커넥션 홀딩 방지 — Discord HTTP 호출을 afterCommit으로 이동
            runAfterCommit(() -> sendFailureAlert(p, ex));
        }
    }

    // @Transactional 미적용 — 호출자(@Transactional 메서드)의 트랜잭션에 참여.
    // 이 클래스 내부에서 self-call 시 @Transactional이 동작하지 않으므로, 필요 시 외부 빈에서 호출할 것.
    public void createRestockAlert(Product product) {
        log.info("재입고 알림 생성: {}", product.getName());

        String message = String.format("[%s] 재입고 - %s",
                product.getShopName() != null ? product.getShopName() : "제품",
                product.getName());

        Alert savedAlert = createAlertInternal(product, Alert.AlertType.PRODUCT_RESTOCK, message);

        // Finding #7: runAfterCommit 헬퍼로 보일러플레이트 제거
        runAfterCommit(() -> {
            broadcastAlert(savedAlert);
            // Finding #5: findActiveSetting 단일 호출 — sendDiscordAlertIfEnabled로 통합
            sendDiscordAlertIfEnabled(url -> discordService.sendProductRestockAlert(url, product));
        });
    }

    public void createContentChangeAlert(Product product) {
        log.info("콘텐츠 변경 알림 생성: {}", product.getName());

        String message = String.format("[%s] 콘텐츠 변경 감지 - %s",
                product.getShopName() != null ? product.getShopName() : "제품",
                product.getName());

        Alert savedAlert = createAlertInternal(product, Alert.AlertType.CONTENT_CHANGE, message);

        // Finding #7: runAfterCommit 헬퍼로 보일러플레이트 제거
        runAfterCommit(() -> {
            broadcastAlert(savedAlert);
            // Finding #5: findActiveSetting 단일 호출 — sendDiscordAlertIfEnabled로 통합
            sendDiscordAlertIfEnabled(url -> discordService.sendProductContentChangeAlert(url, product));
        });
    }

    // Finding #8+#9: createRestockAlert·createContentChangeAlert 공통 로직 헬퍼
    private Alert createAlertInternal(Product product, Alert.AlertType alertType, String message) {
        Alert alert = Alert.builder()
                .product(product)
                .alertType(alertType)
                .message(message)
                .pageTitle(product.getName())
                .detectedUrl(product.getUrl())
                .sent(false)
                .build();
        Alert saved = alertService.createAlert(alert);
        log.info("{} 알림 저장 완료: Alert ID = {}", alertType, saved.getId());
        // Finding #4: getId()는 Hibernate가 SELECT 없이 반환하므로 프록시 초기화 불가 — 불필요한 코드 제거
        return saved;
    }

    // Finding #7: 반복되는 afterCommit 등록 패턴 추출
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void sendDiscordAlertIfEnabled(Consumer<String> discordCall) {
        try {
            settingService.findActiveSetting().ifPresent(setting -> {
                if (setting.enabled() && setting.discordWebhookUrl() != null
                        && !setting.discordWebhookUrl().trim().isEmpty()) {
                    try {
                        discordCall.accept(setting.discordWebhookUrl());
                    } catch (Exception e) {
                        // DiscordService가 이미 에러를 로깅함 — afterCommit 예외 전파 방지
                    }
                }
            });
        } catch (Exception e) {
            log.error("Discord 설정 조회 중 오류", e);
        }
    }

    private void broadcastAlert(Alert alert) {
        try {
            sseService.broadcastAlert(alert);
            log.debug("{} 알림 SSE 브로드캐스트 완료", alert.getAlertType());
        } catch (Exception e) {
            log.error("{} 알림 SSE 브로드캐스트 실패", alert.getAlertType(), e);
        }
    }

    private boolean canSendRestockAlert(Product product) {
        if (product.getLastRestockAlertAt() == null) {
            return true;
        }
        long minutesSinceLastAlert = Duration.between(
                product.getLastRestockAlertAt(), LocalDateTime.now()).toMinutes();
        int cooldownMinutes = monitoringProperties.getRestock().getAlertCooldownMinutes();
        return minutesSinceLastAlert >= cooldownMinutes;
    }

    private boolean canSendContentChangeAlert(Product product) {
        if (product.getLastContentChangeAlertAt() == null) {
            return true;
        }
        long minutesSinceLastAlert = Duration.between(
                product.getLastContentChangeAlertAt(), LocalDateTime.now()).toMinutes();
        int cooldownMinutes = monitoringProperties.getContentChange().getAlertCooldownMinutes();
        return minutesSinceLastAlert >= cooldownMinutes;
    }

    private void sendFailureAlert(Product product, Exception exception) {
        int threshold = monitoringProperties.getFailure().getAlertThreshold();
        log.error("제품 모니터링 {}회 연속 실패: {} ({})",
                threshold, product.getName(), product.getId(), exception);

        String message = String.format(
                "⚠️ **모니터링 연속 실패**\n제품: %s\n연속 실패 횟수: %d회\n마지막 오류: %s",
                product.getName(),
                product.getConsecutiveFailures(),
                exception.getMessage() != null ? exception.getMessage() : "알 수 없는 오류"
        );
        sendDiscordAlertIfEnabled(url -> discordService.sendSimpleMessage(url, message));
    }

    private String generateHash(String content) {
        String hash = HashUtils.sha256(content);
        return hash.isEmpty() ? null : hash;
    }
}
