package com.webmonitor.service;

import com.webmonitor.config.MonitoringProperties;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Product.Priority;
import com.webmonitor.dto.ProductInfo;
import com.webmonitor.exception.resource.ProductNotFoundException;
import com.webmonitor.parser.ProductParser;
import com.webmonitor.parser.ProductParserFactory;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.util.CrawlerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMonitorService {

    private final ProductRepository productRepository;
    private final ProductParserFactory parserFactory;
    private final MonitoringProperties monitoringProperties;
    private final ProductMonitorTransactionHandler productMonitorTransactionHandler;
    private final CrawlerUtils crawlerUtils;

    // @Async 프록시 경유를 위한 self 주입 (@Lazy로 순환 의존성 방지)
    @Lazy
    @Autowired
    private ProductMonitorService self;

    @Transactional
    public void monitorUrgentProducts() {
        List<Product> products = productRepository.findByActiveAndPriority(true, Priority.URGENT);

        for (Product product : products) {
            if (shouldCheckAndUpdateProduct(product)) {
                self.monitorProduct(product);
            }
        }
    }

    @Transactional
    public void monitorNormalProducts() {
        List<Product> products = productRepository.findByActiveAndPriority(true, Priority.NORMAL);

        for (Product product : products) {
            if (shouldCheckAndUpdateProduct(product)) {
                self.monitorProduct(product);
            }
        }
    }

    private boolean shouldCheckAndUpdateProduct(Product product) {
        if (product.getLastCheckedAt() == null) {
            product.setLastCheckedAt(LocalDateTime.now());
            productRepository.save(product);
            return true;
        }

        long secondsElapsed = Duration.between(
                product.getLastCheckedAt(),
                LocalDateTime.now()
        ).toSeconds();

        long thresholdSeconds = product.getCheckIntervalSeconds() != null
                ? product.getCheckIntervalSeconds()
                : (product.getCheckIntervalMinutes() != null ? product.getCheckIntervalMinutes() * 60L : 180L);

        if (secondsElapsed >= thresholdSeconds) {
            product.setLastCheckedAt(LocalDateTime.now());
            productRepository.save(product);
            return true;
        }

        return false;
    }

    @Async("productMonitorExecutor")
    public void monitorProduct(Product product) {
        log.info("[{}] 제품 모니터링 시작: {} ({})",
                Thread.currentThread().getName(),
                product.getName(),
                product.getId());

        try {
            long minDelay = monitoringProperties.getRateLimit().getMinDelayMs();
            long maxDelay = monitoringProperties.getRateLimit().getMaxDelayMs();
            long randomDelay = minDelay + (maxDelay > minDelay ? ThreadLocalRandom.current().nextLong(maxDelay - minDelay) : 0);
            Thread.sleep(randomDelay);

            Document document = crawlerUtils.fetchDocument(product.getUrl());

            ProductParser parser = parserFactory.getParser(product.getUrl());

            if (parser != null) {
                try {
                    ProductInfo info = parser.parseProduct(document, product.getUrl());
                    productMonitorTransactionHandler.processParserBasedMonitoring(product.getId(), info);
                    return;

                } catch (Exception e) {
                    log.warn("파싱 실패, 해시 비교 방식으로 전환: {} - {}",
                            product.getName(), e.getMessage());
                }
            } else {
                log.info("파서 없음 - 해시 비교 방식으로 전환: {}", product.getName());
            }

            productMonitorTransactionHandler.monitorByContentHash(product.getId(), document);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("제품 모니터링 중단됨: {}", product.getName(), e);
            productMonitorTransactionHandler.handleFailure(product.getId(), e);
        } catch (Exception e) {
            log.error("제품 모니터링 중 오류: {}", product.getName(), e);
            productMonitorTransactionHandler.handleFailure(product.getId(), e);
        }
    }

    public void checkProductNow(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        log.info("제품 즉시 체크 요청: {}", product.getName());
        self.monitorProduct(product);
    }
}
