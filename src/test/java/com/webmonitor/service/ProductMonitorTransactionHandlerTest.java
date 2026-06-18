package com.webmonitor.service;

import com.webmonitor.config.MonitoringProperties;
import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Product;
import com.webmonitor.dto.ProductInfo;
import com.webmonitor.dto.SettingResponse;
import com.webmonitor.exception.resource.ProductNotFoundException;
import com.webmonitor.repository.ProductRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductMonitorTransactionHandler 단위 테스트")
class ProductMonitorTransactionHandlerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AlertService alertService;

    @Mock
    private SseService sseService;

    @Mock
    private MonitoringProperties monitoringProperties;

    @Mock
    private SettingService settingService;

    @Mock
    private DiscordService discordService;

    @Spy
    @InjectMocks
    private ProductMonitorTransactionHandler productMonitorTransactionHandler;

    private MonitoringProperties.FailureProperties failureProps;
    private MonitoringProperties.RestockProperties restockProps;

    private MonitoringProperties.ContentChangeProperties contentChangeProps;

    @BeforeEach
    void setUp() {
        failureProps = new MonitoringProperties.FailureProperties();
        failureProps.setAlertThreshold(5);

        restockProps = new MonitoringProperties.RestockProperties();
        restockProps.setAlertCooldownMinutes(60);

        contentChangeProps = new MonitoringProperties.ContentChangeProperties();
        contentChangeProps.setAlertCooldownMinutes(60);

        when(monitoringProperties.getFailure()).thenReturn(failureProps);
        when(monitoringProperties.getRestock()).thenReturn(restockProps);
        when(monitoringProperties.getContentChange()).thenReturn(contentChangeProps);

        when(settingService.findActiveSetting()).thenReturn(Optional.empty());
    }

    // ========== processParserBasedMonitoring Tests ==========

    @Test
    @DisplayName("processParserBasedMonitoring - 상태 변화 없음: 알림 미생성")
    void processParserBasedMonitoring_noStatusChange_noAlert() {
        Product product = buildProduct();
        product.setCurrentStatus(Product.StockStatus.IN_STOCK);
        product.setPreviousStatus(Product.StockStatus.IN_STOCK);

        ProductInfo info = ProductInfo.builder()
                .stockStatus(Product.StockStatus.IN_STOCK)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.processParserBasedMonitoring(1L, info);

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("processParserBasedMonitoring - OUT_OF_STOCK→IN_STOCK: 재입고 알림 생성")
    void processParserBasedMonitoring_outToIn_createsAlert() {
        Product product = buildProduct();
        product.setCurrentStatus(Product.StockStatus.OUT_OF_STOCK);
        product.setNotifyOnRestock(true);
        product.setLastRestockAlertAt(null);

        ProductInfo info = ProductInfo.builder()
                .stockStatus(Product.StockStatus.IN_STOCK)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.processParserBasedMonitoring(1L, info);

        verify(alertService, times(1)).createAlert(any());
    }

    @Test
    @DisplayName("processParserBasedMonitoring - notifyOnRestock=false: 알림 미생성")
    void processParserBasedMonitoring_notifyDisabled_noAlert() {
        Product product = buildProduct();
        product.setCurrentStatus(Product.StockStatus.OUT_OF_STOCK);
        product.setNotifyOnRestock(false);

        ProductInfo info = ProductInfo.builder()
                .stockStatus(Product.StockStatus.IN_STOCK)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.processParserBasedMonitoring(1L, info);

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("processParserBasedMonitoring - 쿨다운 중: 알림 미생성")
    void processParserBasedMonitoring_withinCooldown_noAlert() {
        Product product = buildProduct();
        product.setCurrentStatus(Product.StockStatus.OUT_OF_STOCK);
        product.setNotifyOnRestock(true);
        product.setLastRestockAlertAt(LocalDateTime.now().minusMinutes(30));

        ProductInfo info = ProductInfo.builder()
                .stockStatus(Product.StockStatus.IN_STOCK)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.processParserBasedMonitoring(1L, info);

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("processParserBasedMonitoring - 존재하지 않는 productId: ProductNotFoundException")
    void processParserBasedMonitoring_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ProductInfo info = ProductInfo.builder().stockStatus(Product.StockStatus.IN_STOCK).build();

        assertThatThrownBy(() ->
                productMonitorTransactionHandler.processParserBasedMonitoring(999L, info))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ========== monitorByContentHash Tests ==========

    @Test
    @DisplayName("monitorByContentHash - 최초 체크: 해시 저장, 알림 미생성")
    void monitorByContentHash_firstCheck_storesHashNoAlert() {
        Product product = buildProduct();
        product.setLastContentHash(null);
        product.setNotifyOnRestock(true);

        Document document = Jsoup.parse("<html><body><p>상품 정보</p></body></html>");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        assertThat(product.getLastContentHash()).isNotNull().isNotEmpty();
        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("monitorByContentHash - 해시 동일: 알림 미생성")
    void monitorByContentHash_hashUnchanged_noAlert() {
        Document document = Jsoup.parse("<html><body><p>내용 동일</p></body></html>");
        String existingHash = com.webmonitor.util.HashUtils.sha256(document.body().text());

        Product product = buildProduct();
        product.setLastContentHash(existingHash);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("monitorByContentHash - 해시 변경 + notifyOnContentChange=true + 쿨다운 없음: 알림 생성")
    void monitorByContentHash_hashChanged_createsAlert() {
        Product product = buildProduct();
        product.setLastContentHash("oldhash");
        product.setNotifyOnContentChange(true);
        product.setLastContentChangeAlertAt(null);

        Document document = Jsoup.parse("<html><body><p>완전히 새로운 내용</p></body></html>");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        verify(alertService, times(1)).createAlert(any());
    }

    @Test
    @DisplayName("monitorByContentHash - CSS 셀렉터 지정 + 요소 존재: 셀렉터 콘텐츠로 해시")
    void monitorByContentHash_withSelector_usesSelectorContent() {
        Product product = buildProduct();
        product.setLastContentHash(null);
        product.setContentSelector(".stock-info");

        Document document = Jsoup.parse(
                "<html><body><div class='stock-info'>재고 있음</div><p>기타 정보</p></body></html>");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        assertThat(product.getLastContentHash()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("monitorByContentHash - CSS 셀렉터 없는 요소: fallback으로 body.text()")
    void monitorByContentHash_selectorNotFound_fallsBackToBody() {
        Product product = buildProduct();
        product.setLastContentHash(null);
        product.setContentSelector(".nonexistent");

        Document document = Jsoup.parse("<html><body><p>페이지 내용</p></body></html>");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        assertThat(product.getLastContentHash()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("monitorByContentHash - 존재하지 않는 productId: ProductNotFoundException")
    void monitorByContentHash_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        Document document = Jsoup.parse("<html><body><p>내용</p></body></html>");

        assertThatThrownBy(() ->
                productMonitorTransactionHandler.monitorByContentHash(999L, document))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ========== handleFailure Tests ==========

    @Test
    @DisplayName("handleFailure - 실패 카운터 1 증가")
    void handleFailure_incrementsConsecutiveFailures() {
        Product product = buildProduct();
        product.setConsecutiveFailures(2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.handleFailure(1L, new RuntimeException("오류"));

        assertThat(product.getConsecutiveFailures()).isEqualTo(3);
        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("handleFailure - 임계값 미만: Discord 알림 미전송")
    void handleFailure_belowThreshold_noDiscordAlert() {
        Product product = buildProduct();
        product.setConsecutiveFailures(3);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.handleFailure(1L, new RuntimeException("오류"));

        verifyNoInteractions(discordService);
    }

    @Test
    @DisplayName("handleFailure - 임계값 도달 + settingService webhook 설정: Discord 알림 전송")
    void handleFailure_reachesThreshold_sendsDiscordAlert() {
        SettingResponse setting = new SettingResponse(1L, "https://discord.com/api/webhooks/123/token", true, null, null);
        when(settingService.findActiveSetting()).thenReturn(Optional.of(setting));

        Product product = buildProduct();
        product.setConsecutiveFailures(4);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.handleFailure(1L, new RuntimeException("오류"));

        verify(discordService, times(1)).sendSimpleMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("handleFailure - 임계값 도달 + settingService URL 없음: Discord 미전송")
    void handleFailure_reachesThreshold_noSetting_noDiscord() {
        when(settingService.findActiveSetting()).thenReturn(Optional.empty());

        Product product = buildProduct();
        product.setConsecutiveFailures(4);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.handleFailure(1L, new RuntimeException("오류"));

        verifyNoInteractions(discordService);
    }

    @Test
    @DisplayName("handleFailure - 존재하지 않는 productId: ProductNotFoundException")
    void handleFailure_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productMonitorTransactionHandler.handleFailure(999L, new RuntimeException("오류")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("monitorByContentHash - notifyOnContentChange=false: 알림 미생성")
    void monitorByContentHash_notifyOnContentChangeFalse_noAlert() {
        Product product = buildProduct();
        product.setLastContentHash("oldhash");
        product.setNotifyOnContentChange(false);

        Document document = Jsoup.parse("<html><body><p>완전히 새로운 내용</p></body></html>");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("monitorByContentHash - lastContentChangeAlertAt 쿨다운 중: 알림 미생성")
    void monitorByContentHash_contentChangeCooldown_noAlert() {
        Product product = buildProduct();
        product.setLastContentHash("oldhash");
        product.setNotifyOnContentChange(true);
        product.setLastContentChangeAlertAt(LocalDateTime.now().minusMinutes(30));

        Document document = Jsoup.parse("<html><body><p>완전히 새로운 내용</p></body></html>");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.monitorByContentHash(1L, document);

        verify(alertService, never()).createAlert(any());
    }

    // ========== createRestockAlert Tests ==========

    @Test
    @DisplayName("createRestockAlert - 정상 케이스: alert 생성 + SSE 브로드캐스트")
    void createRestockAlert_happyPath_createsAlertAndBroadcasts() {
        Product product = buildProduct();
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.createRestockAlert(product);

        verify(alertService, times(1)).createAlert(any());
        verify(sseService, times(1)).broadcastAlert(mockAlert);
    }

    @Test
    @DisplayName("createRestockAlert - Discord 직접 호출 없음: 큐 경로(afterCommit)에 위임")
    void createRestockAlert_discordNotCalledDirectly() {
        Product product = buildProduct();
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.createRestockAlert(product);

        verify(discordService, never()).sendProductRestockAlert(anyString(), any());
    }

    @Test
    @DisplayName("createRestockAlert - SSE 브로드캐스트 실패: 예외 전파 없음")
    void createRestockAlert_sseBroadcastFails_doesNotPropagate() {
        Product product = buildProduct();
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);
        doThrow(new RuntimeException("SSE 실패")).when(sseService).broadcastAlert(any());

        productMonitorTransactionHandler.createRestockAlert(product);

        verify(alertService, times(1)).createAlert(any());
    }

    @Test
    @DisplayName("createRestockAlert - SSE 브로드캐스트 + alertService 호출 확인")
    void createRestockAlert_happyPath_alertCreatedAndSseBroadcast() {
        Product product = buildProduct();
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.createRestockAlert(product);

        verify(alertService, times(1)).createAlert(any());
        verify(sseService, times(1)).broadcastAlert(mockAlert);
        verify(discordService, never()).sendProductRestockAlert(anyString(), any());
    }

    // ========== createContentChangeAlert Tests ==========

    @Test
    @DisplayName("createContentChangeAlert - alert 생성 + SSE 브로드캐스트")
    void createContentChangeAlert_happyPath_createsAlertAndBroadcasts() {
        Product product = buildProduct();
        Alert mockAlert = buildMockContentChangeAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.createContentChangeAlert(product);

        verify(alertService, times(1)).createAlert(any());
        verify(sseService, times(1)).broadcastAlert(mockAlert);
    }

    @Test
    @DisplayName("createContentChangeAlert - Discord 호출 확인 (트랜잭션 없는 경로)")
    void createContentChangeAlert_sendsDiscord() {
        SettingResponse setting = new SettingResponse(1L, "https://discord.com/api/webhooks/123/token", true, null, null);
        when(settingService.findActiveSetting()).thenReturn(Optional.of(setting));

        Product product = buildProduct();
        Alert mockAlert = buildMockContentChangeAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        productMonitorTransactionHandler.createContentChangeAlert(product);

        verify(discordService, times(1)).sendProductContentChangeAlert(anyString(), any());
    }

    // ========== handleFailure afterCommit Tests ==========

    @Test
    @DisplayName("handleFailure - 임계값 도달 + 트랜잭션 없음: Discord 즉시 전송")
    void handleFailure_reachesThreshold_noTransaction_sendsDiscordImmediately() {
        SettingResponse setting = new SettingResponse(1L, "https://discord.com/api/webhooks/123/token", true, null, null);
        when(settingService.findActiveSetting()).thenReturn(Optional.of(setting));

        Product product = buildProduct();
        product.setConsecutiveFailures(4);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.handleFailure(1L, new RuntimeException("오류"));

        // TransactionSynchronizationManager.isActualTransactionActive() = false (테스트 환경)
        // → sendFailureAlert 즉시 호출
        verify(discordService, times(1)).sendSimpleMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("handleFailure - Discord 전송 실패 시 예외 비전파")
    void handleFailure_discordThrows_doesNotPropagate() {
        SettingResponse setting = new SettingResponse(1L, "https://discord.com/api/webhooks/123/token", true, null, null);
        when(settingService.findActiveSetting()).thenReturn(Optional.of(setting));
        doThrow(new RuntimeException("Discord 전송 실패")).when(discordService).sendSimpleMessage(anyString(), anyString());

        Product product = buildProduct();
        product.setConsecutiveFailures(4);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productMonitorTransactionHandler.handleFailure(1L, new RuntimeException("모니터링 오류"));

        verify(discordService, times(1)).sendSimpleMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("sendDiscordAlertIfEnabled - settingService 예외 발생 시 Discord 미호출, 예외 비전파")
    void sendDiscordAlertIfEnabled_settingServiceThrows_discordNotCalled() {
        when(settingService.findActiveSetting()).thenThrow(new RuntimeException("DB 연결 오류"));

        Product product = buildProduct();
        Alert mockAlert = buildMockAlert();
        when(alertService.createAlert(any())).thenReturn(mockAlert);

        // 예외가 밖으로 전파되지 않아야 하고, discordService는 호출되지 않아야 함
        productMonitorTransactionHandler.createRestockAlert(product);

        verify(discordService, never()).sendProductRestockAlert(anyString(), any());
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

    private Alert buildMockAlert() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message("재입고 알림")
                .detectedUrl("https://shop.com/product")
                .build();
        ReflectionTestUtils.setField(alert, "id", 10L);
        return alert;
    }

    private Alert buildMockContentChangeAlert() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.CONTENT_CHANGE)
                .message("콘텐츠 변경 알림")
                .detectedUrl("https://shop.com/product")
                .build();
        ReflectionTestUtils.setField(alert, "id", 11L);
        return alert;
    }
}
