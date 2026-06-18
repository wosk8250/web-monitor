package com.webmonitor.service;

import com.webmonitor.config.MonitoringProperties;
import com.webmonitor.domain.Product;
import com.webmonitor.exception.resource.ProductNotFoundException;
import com.webmonitor.parser.ProductParserFactory;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.util.CrawlerUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductMonitorService 단위 테스트")
class ProductMonitorServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductParserFactory parserFactory;

    @Mock
    private MonitoringProperties monitoringProperties;

    @Mock
    private ProductMonitorTransactionHandler productMonitorTransactionHandler;

    @Mock
    private CrawlerUtils crawlerUtils;

    @Spy
    @InjectMocks
    private ProductMonitorService productMonitorService;

    private MonitoringProperties.RateLimitProperties rateLimitProps;

    @BeforeEach
    void setUp() {
        rateLimitProps = new MonitoringProperties.RateLimitProperties();
        rateLimitProps.setMinDelayMs(0);
        rateLimitProps.setMaxDelayMs(0);

        when(monitoringProperties.getRateLimit()).thenReturn(rateLimitProps);

        // @Lazy @Autowired self는 @InjectMocks가 주입하지 않으므로 spy 자신을 수동 세팅
        ReflectionTestUtils.setField(productMonitorService, "self", productMonitorService);
    }

    // ========== monitorUrgentProducts Tests ==========

    @Test
    @DisplayName("monitorUrgentProducts - 빈 목록: 모니터링 스킵")
    void monitorUrgentProducts_emptyList_skipsMonitoring() {
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));
        when(productRepository.findByActiveAndPriority(true, Product.Priority.URGENT))
                .thenReturn(List.of());

        productMonitorService.monitorUrgentProducts();

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("monitorUrgentProducts - lastCheckedAt null 제품: shouldCheck=true, save 호출")
    void monitorUrgentProducts_lastCheckedAtNull_savesTimestamp() {
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));
        Product product = buildProduct();
        product.setLastCheckedAt(null);

        when(productRepository.findByActiveAndPriority(true, Product.Priority.URGENT))
                .thenReturn(List.of(product));

        productMonitorService.monitorUrgentProducts();

        verify(productRepository, times(1)).save(product);
        assertThat(product.getLastCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("monitorUrgentProducts - 체크 주기 미경과 제품: save 미호출")
    void monitorUrgentProducts_withinInterval_skips() {
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));
        Product product = buildProduct();
        product.setLastCheckedAt(LocalDateTime.now());
        product.setCheckIntervalMinutes(5);

        when(productRepository.findByActiveAndPriority(true, Product.Priority.URGENT))
                .thenReturn(List.of(product));

        productMonitorService.monitorUrgentProducts();

        verify(productRepository, never()).save(product);
    }

    @Test
    @DisplayName("monitorUrgentProducts - 체크 주기 경과 제품: save 호출")
    void monitorUrgentProducts_intervalElapsed_savesTimestamp() {
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));
        Product product = buildProduct();
        product.setLastCheckedAt(LocalDateTime.now().minusMinutes(10));
        product.setCheckIntervalMinutes(5);

        when(productRepository.findByActiveAndPriority(true, Product.Priority.URGENT))
                .thenReturn(List.of(product));

        productMonitorService.monitorUrgentProducts();

        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("monitorUrgentProducts - checkIntervalSeconds 20초 설정, 30초 경과: save 호출")
    void monitorUrgentProducts_checkIntervalSeconds_elapsed_savesTimestamp() {
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));
        Product product = buildProduct();
        product.setLastCheckedAt(LocalDateTime.now().minusSeconds(30));
        product.setCheckIntervalSeconds(20);

        when(productRepository.findByActiveAndPriority(true, Product.Priority.URGENT))
                .thenReturn(List.of(product));

        productMonitorService.monitorUrgentProducts();

        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("monitorUrgentProducts - checkIntervalSeconds 20초 설정, 10초만 경과: save 미호출")
    void monitorUrgentProducts_checkIntervalSeconds_notElapsed_skips() {
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));
        Product product = buildProduct();
        product.setLastCheckedAt(LocalDateTime.now().minusSeconds(10));
        product.setCheckIntervalSeconds(20);

        when(productRepository.findByActiveAndPriority(true, Product.Priority.URGENT))
                .thenReturn(List.of(product));

        productMonitorService.monitorUrgentProducts();

        verify(productRepository, never()).save(product);
    }

    // ========== checkProductNow Tests ==========

    @Test
    @DisplayName("checkProductNow - 존재하는 productId: monitorProduct 호출")
    // 참고: self는 raw spy이므로 @Async 프록시를 경유하지 않음. 비동기 디스패치 자체는 통합 테스트에서 검증 필요.
    void checkProductNow_found_callsMonitorProduct() {
        Product product = buildProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        doNothing().when(productMonitorService).monitorProduct(any(Product.class));

        productMonitorService.checkProductNow(1L);

        verify(productMonitorService).monitorProduct(product);
    }

    @Test
    @DisplayName("checkProductNow - 존재하지 않는 productId: ProductNotFoundException")
    void checkProductNow_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productMonitorService.checkProductNow(999L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ========== Helpers ==========

    private Product buildProduct() {
        Product product = Product.builder()
                .name("테스트 제품")
                .url("https://shop.com/product")
                .currentStatus(Product.StockStatus.OUT_OF_STOCK)
                .previousStatus(Product.StockStatus.OUT_OF_STOCK)
                .active(true)
                .notifyOnRestock(true)
                .priority(Product.Priority.NORMAL)
                .checkIntervalMinutes(3)
                .consecutiveFailures(0)
                .build();
        ReflectionTestUtils.setField(product, "id", 1L);
        return product;
    }
}
