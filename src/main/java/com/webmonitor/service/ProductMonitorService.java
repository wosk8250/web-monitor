package com.webmonitor.service;

import com.webmonitor.config.MonitoringProperties;
import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Product.Priority;
import com.webmonitor.domain.Product.StockStatus;
import com.webmonitor.dto.ProductInfo;
import com.webmonitor.parser.ProductParser;
import com.webmonitor.parser.ProductParserFactory;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.repository.SettingRepository;
import com.webmonitor.util.HashUtils;
import com.webmonitor.util.WebCrawlerConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 제품 재고 모니터링 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMonitorService {



    private final ProductRepository productRepository;
    private final AlertRepository alertRepository;
    private final ProductParserFactory parserFactory;
    private final SseService sseService;
    private final DiscordService discordService;
    private final MonitoringProperties monitoringProperties;
    private final AlertService alertService; // 알림 서비스 (우선순위 기반 처리)

    @Value("${discord.webhook.url:}")
    private String discordWebhookUrl;

    /**
     * 긴급 우선순위 제품 모니터링 (URGENT)
     * 30초 스케줄러에서 호출
     *
     * Race Condition 방지: lastCheckedAt을 먼저 업데이트하여 중복 체크 방지
     */
    @Transactional
    public void monitorUrgentProducts() {
        List<Product> products = productRepository.findByActiveAndPriority(true, Priority.URGENT);

        for (Product product : products) {
            if (shouldCheckAndUpdateProduct(product)) {
                monitorProduct(product);
            }
        }
    }

    /**
     * 일반 우선순위 제품 모니터링 (NORMAL)
     * 60초 스케줄러에서 호출
     *
     * Race Condition 방지: lastCheckedAt을 먼저 업데이트하여 중복 체크 방지
     */
    @Transactional
    public void monitorNormalProducts() {
        List<Product> products = productRepository.findByActiveAndPriority(true, Priority.NORMAL);

        for (Product product : products) {
            if (shouldCheckAndUpdateProduct(product)) {
                monitorProduct(product);
            }
        }
    }

    /**
     * 제품 체크 주기 확인 및 lastCheckedAt 업데이트 (Race Condition 방지)
     *
     * @return true면 체크 진행, false면 스킵
     */
    private boolean shouldCheckAndUpdateProduct(Product product) {
        // 처음 체크하는 경우
        if (product.getLastCheckedAt() == null) {
            // 즉시 lastCheckedAt 업데이트하여 다른 스케줄러의 중복 체크 방지
            product.setLastCheckedAt(LocalDateTime.now());
            productRepository.save(product);
            return true;
        }

        // 설정된 주기가 지났는지 확인
        long minutesElapsed = Duration.between(
                product.getLastCheckedAt(),
                LocalDateTime.now()
        ).toMinutes();

        if (minutesElapsed >= product.getCheckIntervalMinutes()) {
            // 즉시 lastCheckedAt 업데이트하여 다른 스케줄러의 중복 체크 방지
            product.setLastCheckedAt(LocalDateTime.now());
            productRepository.save(product);
            return true;
        }

        return false;
    }

    /**
     * 개별 제품 모니터링 (비동기 병렬 처리)
     * @Async만 사용 - 트랜잭션은 내부 메서드에서 별도 처리
     */
    @Async("productMonitorExecutor")
    public void monitorProduct(Product product) {
        log.info("[{}] 제품 모니터링 시작: {} ({})",
                Thread.currentThread().getName(),
                product.getName(),
                product.getId());

        try {
            // Rate limiting (요청 간 딜레이) - 트랜잭션 밖에서 처리
            long minDelay = monitoringProperties.getRateLimit().getMinDelayMs();
            long maxDelay = monitoringProperties.getRateLimit().getMaxDelayMs();
            long randomDelay = minDelay + (long) (Math.random() * (maxDelay - minDelay));
            Thread.sleep(randomDelay);

            // 1. HTML 다운로드 (한 번만 수행) - 트랜잭션 밖에서 처리
            Document document = fetchDocument(product.getUrl());

            // 2. 파서 시도
            ProductParser parser = parserFactory.getParser(product.getUrl());

            if (parser != null) {
                try {
                    // 파싱 시도 - 트랜잭션 밖에서 처리
                    ProductInfo info = parser.parseProduct(document, product.getUrl());

                    // 파싱 성공 - DB 업데이트는 별도 트랜잭션으로
                    processParserBasedMonitoringInternal(product.getId(), info);
                    return;

                } catch (Exception e) {
                    log.warn("파싱 실패, 해시 비교 방식으로 전환: {} - {}",
                            product.getName(), e.getMessage());
                }
            } else {
                log.info("파서 없음 - 해시 비교 방식으로 전환: {}", product.getName());
            }

            // 3. 파서 없거나 파싱 실패 - Document 재사용하여 해시 비교
            monitorProductByContentHashInternal(product.getId(), document);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("제품 모니터링 중단됨: {}", product.getName(), e);
            handleMonitoringFailure(product.getId(), e);
        } catch (Exception e) {
            log.error("제품 모니터링 중 오류: {}", product.getName(), e);
            handleMonitoringFailure(product.getId(), e);
        }
    }

    /**
     * URL 보안 검증 (SSRF 공격 방어)
     */
    private void validateUrl(String urlString) {
        try {
            URL url = new URL(urlString);

            // 1. 프로토콜 제한 (HTTP/HTTPS만 허용)
            if (!url.getProtocol().matches("https?")) {
                throw new IllegalArgumentException("HTTP 또는 HTTPS 프로토콜만 허용됩니다: " + url.getProtocol());
            }

            // 2. 내부 IP 차단 (SSRF 방어)
            InetAddress address = InetAddress.getByName(url.getHost());
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("내부 IP 주소는 허용되지 않습니다: " + address.getHostAddress());
            }

            // 3. 포트 제한 (80, 443만 허용)
            int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
            if (port != 80 && port != 443) {
                throw new IllegalArgumentException("80 또는 443 포트만 허용됩니다: " + port);
            }

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("유효하지 않은 URL 형식입니다: " + urlString, e);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("호스트를 찾을 수 없습니다: " + urlString, e);
        }
    }

    /**
     * HTML 다운로드 (재시도 로직 포함)
     * Circuit Breaker 적용: 외부 API 호출 실패 시 빠른 실패로 cascading failure 방지
     */
    @CircuitBreaker(name = "webCrawler", fallbackMethod = "fetchDocumentFallback")
    private Document fetchDocument(String url) throws IOException {
        // SSRF 공격 방어를 위한 URL 검증
        validateUrl(url);

        int attempt = 0;
        IOException lastException = null;

        while (attempt < WebCrawlerConstants.MAX_RETRY_ATTEMPTS) {
            attempt++;
            try {
                // User-Agent 랜덤 선택 (크롤링 차단 방지)
                String userAgent = WebCrawlerConstants.USER_AGENTS[ThreadLocalRandom.current().nextInt(WebCrawlerConstants.USER_AGENTS.length)];

                Document document = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .timeout(WebCrawlerConstants.TIMEOUT_LONG_MS)
                        .maxBodySize(1024 * 1024)  // 1MB 제한 (메모리 보호)
                        .ignoreHttpErrors(false)    // HTTP 에러도 처리
                        .followRedirects(true)      // 리다이렉트 허용
                        .get();

                if (attempt > 1) {
                    log.info("제품 HTML 다운로드 재시도 성공: 시도 {}, URL: {}", attempt, url);
                }
                return document;

            } catch (HttpStatusException e) {
                lastException = e;

                // HTTP 상태 코드별 처리
                switch (e.getStatusCode()) {
                    case 404:
                        log.error("제품 페이지를 찾을 수 없음 (404): {}", url);
                        throw new IOException("제품 페이지를 찾을 수 없습니다 (404): " + url, e);

                    case 429:
                        log.warn("요청 제한 (429) - 대기 후 재시도: {}", url);
                        break;

                    case 500:
                    case 502:
                    case 503:
                        log.warn("서버 오류 ({}): {} - 재시도 중", e.getStatusCode(), url);
                        break;

                    default:
                        log.warn("HTTP 오류 ({}): {} - 재시도 중", e.getStatusCode(), url);
                        break;
                }

            } catch (SocketTimeoutException e) {
                lastException = e;
                log.warn("제품 HTML 다운로드 타임아웃 (시도 {}/{}): {}", attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url);

            } catch (UnknownHostException e) {
                lastException = e;
                log.error("제품 URL 호스트를 찾을 수 없음: {}", url);
                throw new IOException("호스트를 찾을 수 없습니다: " + url, e);

            } catch (ConnectException e) {
                lastException = e;
                log.warn("제품 URL 연결 실패 (시도 {}/{}): {} - {}",
                        attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url, e.getMessage());

            } catch (IOException e) {
                lastException = e;
                log.warn("제품 HTML 다운로드 실패 (시도 {}/{}): {} - 오류: {}",
                        attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url, e.getMessage());
            }

            // 재시도 대기 (Exponential Backoff)
            if (attempt < WebCrawlerConstants.MAX_RETRY_ATTEMPTS) {
                long waitTime = WebCrawlerConstants.INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("재시도 대기 중 인터럽트 발생", ie);
                }
            }
        }

        // 모든 재시도 실패
        log.error("제품 HTML 다운로드 최대 재시도 횟수 초과: {}", url);
        throw new IOException("제품 HTML 다운로드 실패 (최대 재시도 " + WebCrawlerConstants.MAX_RETRY_ATTEMPTS + "회): " + url, lastException);
    }

    /**
     * Circuit Breaker Fallback 메서드
     * Circuit이 OPEN 상태일 때 호출됩니다.
     */
    private Document fetchDocumentFallback(String url, Throwable throwable) throws IOException {
        log.error("Circuit Breaker OPEN: 외부 웹사이트 크롤링 차단됨 (URL: {}) - 이유: {}",
                url, throwable.getMessage());
        throw new IOException("Circuit Breaker OPEN: 외부 웹사이트가 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.", throwable);
    }

    /**
     * 파서 기반 모니터링 처리 (트랜잭션 내부)
     * ID 기반으로 재조회하여 Detached 엔티티 문제 방지
     */
    @Transactional
    protected void processParserBasedMonitoringInternal(Long productId, ProductInfo info) {
        // 1. Product를 ID로 재조회
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + productId));

        // 2. 이전 상태 저장
        product.setPreviousStatus(product.getCurrentStatus());
        product.setPreviousPrice(product.getCurrentPrice());

        // 3. 현재 상태 업데이트
        product.setCurrentStatus(info.getStockStatus());
        product.setCurrentPrice(info.getPrice());
        product.setImageUrl(info.getImageUrl());
        product.setShopName(info.getShopName());

        // 4. 재입고 감지 (OUT_OF_STOCK → IN_STOCK)
        if (product.getNotifyOnRestock() &&
            product.getPreviousStatus() == StockStatus.OUT_OF_STOCK &&
            product.getCurrentStatus() == StockStatus.IN_STOCK) {

            log.info("재입고 감지: {}", product.getName());

            // 중복 알림 방지 (1시간 내 재알림 차단)
            if (canSendRestockAlert(product)) {
                createRestockAlert(product);
                product.setLastRestockAlertAt(LocalDateTime.now());
            } else {
                log.info("재입고 알림 스킵 (최근 1시간 내 발송됨): {}", product.getName());
            }
        }

        // 5. 성공 시 실패 카운터 초기화
        product.setConsecutiveFailures(0);
        product.setLastCheckedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("[{}] 제품 모니터링 완료: {} - 상태: {}",
                Thread.currentThread().getName(),
                product.getName(),
                product.getCurrentStatus());
    }


    /**
     * 재입고 알림 전송 가능 여부 확인 (쿨다운 시간 내 재알림 차단)
     */
    private boolean canSendRestockAlert(Product product) {
        if (product.getLastRestockAlertAt() == null) {
            return true;
        }

        long minutesSinceLastAlert = Duration.between(
                product.getLastRestockAlertAt(),
                LocalDateTime.now()
        ).toMinutes();

        int cooldownMinutes = monitoringProperties.getRestock().getAlertCooldownMinutes();
        return minutesSinceLastAlert >= cooldownMinutes;
    }

    /**
     * 재입고 알림 생성
     */
    @Transactional
    public void createRestockAlert(Product product) {
        log.info("재입고 알림 생성: {}", product.getName());

        String message = String.format("[%s] 재입고 - %s",
                product.getShopName() != null ? product.getShopName() : "제품",
                product.getName());

        Alert alert = Alert.builder()
                .product(product)
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message(message)
                .pageTitle(product.getName())
                .detectedUrl(product.getUrl())
                .sent(false)
                .build();

        Alert savedAlert = alertService.createAlert(alert);
        log.info("재입고 알림 저장 완료: Alert ID = {}", savedAlert.getId());

        // SSE로 실시간 알림 전송
        try {
            sseService.broadcastAlert(savedAlert);
            log.debug("재입고 알림 SSE 브로드캐스트 완료");
        } catch (Exception e) {
            log.error("재입고 알림 SSE 브로드캐스트 실패", e);
        }

        // Discord 알림 전송
        try {
            if (discordWebhookUrl != null && !discordWebhookUrl.trim().isEmpty()) {
                discordService.sendProductRestockAlert(discordWebhookUrl, product);
            }
        } catch (Exception e) {
            log.error("디스코드 제품 재입고 알림 전송 중 오류 발생", e);
        }
    }

    /**
     * 특정 제품 즉시 체크 (Discord 명령어용)
     * 트랜잭션 없음: monitorProduct()가 @Async로 비동기 실행되므로 불필요
     */
    public void checkProductNow(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + productId));

        log.info("제품 즉시 체크 요청: {}", product.getName());
        monitorProduct(product);
    }

    /**
     * 콘텐츠 해시 비교 방식으로 제품 모니터링 (트랜잭션 내부)
     * ID 기반으로 재조회하여 Detached 엔티티 문제 방지
     */
    @Transactional
    protected void monitorProductByContentHashInternal(Long productId, Document document) {
        try {
            // 1. Product를 ID로 재조회
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + productId));

            // 2. 모니터링할 콘텐츠 추출
            String content;
            if (product.getContentSelector() != null && !product.getContentSelector().trim().isEmpty()) {
                // CSS 셀렉터가 지정된 경우 해당 요소만 추출
                Element element = document.selectFirst(product.getContentSelector());
                if (element != null) {
                    content = element.text();
                } else {
                    log.warn("셀렉터로 요소를 찾을 수 없음: {}", product.getContentSelector());
                    content = document.body().text();
                }
            } else {
                // 셀렉터가 없으면 전체 페이지
                content = document.body().text();
            }

            // 3. SHA-256 해시 생성
            String currentHash = generateHash(content);

            // 해시 생성 실패 시 처리
            if (currentHash == null) {
                log.error("해시 생성 실패로 모니터링 스킵: {}", product.getName());
                product.setLastCheckedAt(LocalDateTime.now());
                productRepository.save(product);
                return;
            }

            // 4. 이전 해시와 비교
            String previousHash = product.getLastContentHash();
            boolean isFirstCheck = (previousHash == null);
            boolean contentChanged = !isFirstCheck && !currentHash.equals(previousHash);

            // 5. 변경 감지 시 알림 (최초 체크는 알림 안 함)
            if (contentChanged && product.getNotifyOnRestock()) {
                log.info("콘텐츠 변경 감지: {}", product.getName());

                // 중복 알림 방지 (1시간 내 재알림 차단)
                if (canSendRestockAlert(product)) {
                    createRestockAlert(product);
                    product.setLastRestockAlertAt(LocalDateTime.now());
                } else {
                    log.info("변경 감지 알림 스킵 (최근 1시간 내 발송됨): {}", product.getName());
                }
            }

            // 6. 해시 업데이트 및 성공 시 실패 카운터 초기화
            product.setLastContentHash(currentHash);
            product.setConsecutiveFailures(0);
            product.setLastCheckedAt(LocalDateTime.now());
            productRepository.save(product);

            log.info("[{}] 콘텐츠 해시 모니터링 완료: {}",
                    Thread.currentThread().getName(),
                    product.getName());

        } catch (Exception e) {
            log.error("콘텐츠 해시 모니터링 중 오류 (productId: {})", productId, e);
            handleMonitoringFailure(productId, e);
        }
    }

    /**
     * 모니터링 실패 처리 (트랜잭션 내부)
     * ID 기반으로 재조회하여 Detached 엔티티 문제 방지
     */
    @Transactional
    protected void handleMonitoringFailure(Long productId, Exception exception) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + productId));

        product.setConsecutiveFailures(product.getConsecutiveFailures() + 1);
        product.setLastFailureAt(LocalDateTime.now());
        product.setLastCheckedAt(LocalDateTime.now());
        productRepository.save(product);

        log.warn("제품 모니터링 실패 (연속 {}회): {}",
                product.getConsecutiveFailures(), product.getName());

        // 연속 실패 임계값 도달 시 알림
        int alertThreshold = monitoringProperties.getFailure().getAlertThreshold();
        if (product.getConsecutiveFailures() >= alertThreshold) {
            sendFailureAlert(product, exception);
        }
    }

    /**
     * 모니터링 연속 실패 알림 전송
     */
    private void sendFailureAlert(Product product, Exception exception) {
        int threshold = monitoringProperties.getFailure().getAlertThreshold();
        log.error("제품 모니터링 {}회 연속 실패: {} ({})",
                threshold, product.getName(), product.getId());

        try {
            if (discordWebhookUrl != null && !discordWebhookUrl.trim().isEmpty()) {
                String message = String.format(
                    "⚠️ **모니터링 연속 실패**\n" +
                    "제품: %s\n" +
                    "연속 실패 횟수: %d회\n" +
                    "마지막 오류: %s",
                    product.getName(),
                    product.getConsecutiveFailures(),
                    exception.getMessage() != null ? exception.getMessage() : "알 수 없는 오류"
                );

                discordService.sendSimpleMessage(discordWebhookUrl, message);
            }
        } catch (Exception e) {
            log.error("실패 알림 전송 중 오류", e);
        }
    }

    /**
     * 문자열의 SHA-256 해시 생성
     */
    private String generateHash(String content) {
        String hash = HashUtils.sha256(content);
        return hash.isEmpty() ? null : hash;
    }
}
