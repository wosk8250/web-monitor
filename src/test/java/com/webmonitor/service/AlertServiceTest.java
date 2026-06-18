package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.exception.resource.AlertNotFoundException;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Product.StockStatus;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AlertService 통합 테스트
 */
@SpringBootTest
@Transactional
class AlertServiceTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private ProductRepository productRepository;

    private Site testSite;
    private Keyword testKeyword;
    private Product testProduct;
    private Alert testAlert;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        alertRepository.deleteAll();
        keywordRepository.deleteAll();
        productRepository.deleteAll();
        siteRepository.deleteAll();

        // 테스트용 사이트 생성
        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .build();
        testSite = siteRepository.save(testSite);

        // 테스트용 키워드 생성
        testKeyword = Keyword.builder()
                .keyword("테스트")
                .site(testSite)
                .active(true)
                .build();
        testKeyword = keywordRepository.save(testKeyword);

        // 테스트용 제품 생성
        testProduct = Product.builder()
                .name("테스트 제품")
                .url("https://test.com/product")
                .active(true)
                .currentStatus(StockStatus.OUT_OF_STOCK)
                .build();
        testProduct = productRepository.save(testProduct);

        // 테스트용 알림 생성 (KEYWORD 타입)
        testAlert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("키워드 감지됨")
                .pageTitle("테스트 페이지")
                .detectedUrl("https://test.com/page")
                .site(testSite)
                .keyword(testKeyword)
                .sent(false)
                .build();
        testAlert = alertRepository.save(testAlert);
    }

    @Test
    @DisplayName("키워드 알림 생성 성공 - NORMAL 우선순위")
    void createAlert_KeywordAlert_NormalPriority() {
        // Given
        Alert newAlert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("새 키워드 알림")
                .pageTitle("새 페이지")
                .detectedUrl("https://test.com/new")
                .site(testSite)
                .keyword(testKeyword)
                .build();

        // When
        Alert created = alertService.createAlert(newAlert);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getMessage()).isEqualTo("새 키워드 알림");
        assertThat(created.getPriority()).isEqualTo(Alert.Priority.NORMAL); // @PrePersist에서 설정
        assertThat(created.getSent()).isFalse();
        assertThat(created.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("제품 재입고 알림 생성 성공 - HIGH 우선순위")
    void createAlert_ProductRestockAlert_HighPriority() {
        // Given
        Alert restockAlert = Alert.builder()
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message("제품 재입고")
                .pageTitle("제품 페이지")
                .detectedUrl("https://test.com/product")
                .product(testProduct)
                .build();

        // When
        Alert created = alertService.createAlert(restockAlert);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getPriority()).isEqualTo(Alert.Priority.HIGH); // @PrePersist에서 설정
        assertThat(created.getAlertType()).isEqualTo(Alert.AlertType.PRODUCT_RESTOCK);
    }

    @Test
    @DisplayName("내용 변경 알림 생성 성공 - NORMAL 우선순위")
    void createAlert_ContentChangeAlert_NormalPriority() {
        // Given
        Alert contentChangeAlert = Alert.builder()
                .alertType(Alert.AlertType.CONTENT_CHANGE)
                .message("페이지 내용 변경됨")
                .pageTitle("변경된 페이지")
                .detectedUrl("https://test.com/changed")
                .site(testSite)
                .build();

        // When
        Alert created = alertService.createAlert(contentChangeAlert);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getPriority()).isEqualTo(Alert.Priority.NORMAL);
        assertThat(created.getAlertType()).isEqualTo(Alert.AlertType.CONTENT_CHANGE);
    }

    @Test
    @DisplayName("ID로 알림 조회 성공")
    void getAlertById_Success() {
        // When
        Alert found = alertService.getAlertById(testAlert.getId());

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getMessage()).isEqualTo("키워드 감지됨");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 AlertNotFoundException 발생")
    void getAlertById_NotFound_ThrowsAlertNotFoundException() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> alertService.getAlertById(nonExistentId))
                .isInstanceOf(com.webmonitor.exception.resource.AlertNotFoundException.class);
    }

    @Test
    @DisplayName("모든 알림 조회 (페이지네이션)")
    void getAllAlerts_ReturnsPagedAlerts() {
        // Given
        Alert alert2 = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("알림 2")
                .pageTitle("페이지 2")
                .detectedUrl("https://test.com/page2")
                .site(testSite)
                .keyword(testKeyword)
                .build();
        alertRepository.save(alert2);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> alertsPage = alertService.getAllAlerts(pageable);

        // Then
        assertThat(alertsPage.getContent()).hasSize(2);
        assertThat(alertsPage.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("미발송 알림 조회 (페이지네이션)")
    void getUnsentAlerts_ReturnsOnlyUnsent() {
        // Given
        Alert sentAlert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("발송된 알림")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/sent")
                .site(testSite)
                .sent(true)
                .sentAt(LocalDateTime.now())
                .build();
        alertRepository.save(sentAlert);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> unsentAlerts = alertService.getUnsentAlerts(pageable);

        // Then
        assertThat(unsentAlerts.getContent()).hasSize(1);
        assertThat(unsentAlerts.getContent().get(0).getSent()).isFalse();
    }

    @Test
    @DisplayName("발송 완료 알림 조회 (페이지네이션)")
    void getSentAlerts_ReturnsOnlySent() {
        // Given
        Alert sentAlert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("발송된 알림")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/sent")
                .site(testSite)
                .sent(true)
                .sentAt(LocalDateTime.now())
                .build();
        alertRepository.save(sentAlert);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> sentAlerts = alertService.getSentAlerts(pageable);

        // Then
        assertThat(sentAlerts.getContent()).hasSize(1);
        assertThat(sentAlerts.getContent().get(0).getSent()).isTrue();
    }

    @Test
    @DisplayName("특정 사이트 알림 조회 (페이지네이션)")
    void getAlertsBySite_ReturnsCorrectAlerts() {
        // Given
        Site anotherSite = Site.builder()
                .name("다른 사이트")
                .url("https://another.com")
                .active(true)
                .build();
        anotherSite = siteRepository.save(anotherSite);

        Alert alertForAnotherSite = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("다른 사이트 알림")
                .pageTitle("페이지")
                .detectedUrl("https://another.com/page")
                .site(anotherSite)
                .build();
        alertRepository.save(alertForAnotherSite);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> alertsForTestSite = alertService.getAlertsBySite(testSite.getId(), pageable);
        Page<Alert> alertsForAnotherSite = alertService.getAlertsBySite(anotherSite.getId(), pageable);

        // Then
        assertThat(alertsForTestSite.getContent()).hasSize(1);
        assertThat(alertsForTestSite.getContent().get(0).getSite().getId()).isEqualTo(testSite.getId());
        assertThat(alertsForAnotherSite.getContent()).hasSize(1);
        assertThat(alertsForAnotherSite.getContent().get(0).getSite().getId()).isEqualTo(anotherSite.getId());
    }

    @Test
    @DisplayName("특정 키워드 알림 조회 (페이지네이션)")
    void getAlertsByKeyword_ReturnsCorrectAlerts() {
        // Given
        Keyword anotherKeyword = Keyword.builder()
                .keyword("다른 키워드")
                .site(testSite)
                .active(true)
                .build();
        anotherKeyword = keywordRepository.save(anotherKeyword);

        Alert alertForAnotherKeyword = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("다른 키워드 알림")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/other")
                .site(testSite)
                .keyword(anotherKeyword)
                .build();
        alertRepository.save(alertForAnotherKeyword);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> alertsForTestKeyword = alertService.getAlertsByKeyword(testKeyword.getId(), pageable);
        Page<Alert> alertsForAnotherKeyword = alertService.getAlertsByKeyword(anotherKeyword.getId(), pageable);

        // Then
        assertThat(alertsForTestKeyword.getContent()).hasSize(1);
        assertThat(alertsForTestKeyword.getContent().get(0).getKeyword().getId()).isEqualTo(testKeyword.getId());
        assertThat(alertsForAnotherKeyword.getContent()).hasSize(1);
        assertThat(alertsForAnotherKeyword.getContent().get(0).getKeyword().getId()).isEqualTo(anotherKeyword.getId());
    }

    @Test
    @DisplayName("특정 기간 알림 조회")
    void getAlertsByPeriod_ReturnsCorrectAlerts() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(1);
        LocalDateTime endDate = now.plusDays(1);

        // When
        List<Alert> alerts = alertService.getAlertsByPeriod(startDate, endDate);

        // Then
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDetectedAt()).isBetween(startDate, endDate);
    }

    @Test
    @DisplayName("기간 밖의 알림은 조회되지 않음")
    void getAlertsByPeriod_ExcludesOutsidePeriod() {
        // Given
        LocalDateTime futureStart = LocalDateTime.now().plusDays(2);
        LocalDateTime futureEnd = LocalDateTime.now().plusDays(3);

        // When
        List<Alert> alerts = alertService.getAlertsByPeriod(futureStart, futureEnd);

        // Then
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("알림을 발송 완료로 표시 성공")
    void markAsSent_Success() {
        // Given
        assertThat(testAlert.getSent()).isFalse();
        assertThat(testAlert.getSentAt()).isNull();

        // When
        Alert marked = alertService.markAsSent(testAlert.getId());

        // Then
        assertThat(marked.getSent()).isTrue();
        assertThat(marked.getSentAt()).isNotNull();
        assertThat(marked.getSentAt()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("존재하지 않는 알림 발송 완료 표시 시 예외 발생")
    void markAsSent_NotFound_ThrowsException() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> alertService.markAsSent(nonExistentId))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("알림을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("알림 삭제 성공")
    void deleteAlert_Success() {
        // When
        alertService.deleteAlert(testAlert.getId());

        // Then
        assertThat(alertRepository.count()).isEqualTo(0);
        assertThat(alertRepository.findById(testAlert.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 알림 삭제 시 예외 발생")
    void deleteAlert_NotFound_ThrowsException() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> alertService.deleteAlert(nonExistentId))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("알림을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("전송된 알림 일괄 삭제 성공")
    void deleteSentAlerts_DeletesOnlySent() {
        // Given
        Alert sentAlert1 = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("발송된 알림 1")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/sent1")
                .site(testSite)
                .sent(true)
                .sentAt(LocalDateTime.now())
                .build();
        alertRepository.save(sentAlert1);

        Alert sentAlert2 = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("발송된 알림 2")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/sent2")
                .site(testSite)
                .sent(true)
                .sentAt(LocalDateTime.now())
                .build();
        alertRepository.save(sentAlert2);

        // 총 3개 알림: 1개 미발송(testAlert), 2개 발송됨
        assertThat(alertRepository.count()).isEqualTo(3);

        // When
        int deletedCount = alertService.deleteSentAlerts();

        // Then
        assertThat(deletedCount).isEqualTo(2);
        assertThat(alertRepository.count()).isEqualTo(1); // 미발송 알림만 남음
        assertThat(alertRepository.findById(testAlert.getId())).isPresent(); // 미발송 알림은 유지
    }

    @Test
    @DisplayName("전송된 알림이 없을 때 일괄 삭제 시 0 반환")
    void deleteSentAlerts_NoSentAlerts_ReturnsZero() {
        // Given
        assertThat(alertRepository.count()).isEqualTo(1);
        assertThat(testAlert.getSent()).isFalse();

        // When
        int deletedCount = alertService.deleteSentAlerts();

        // Then
        assertThat(deletedCount).isEqualTo(0);
        assertThat(alertRepository.count()).isEqualTo(1); // 변경 없음
    }

    @Test
    @DisplayName("전체 알림 삭제 성공")
    void deleteAllAlerts_Success() {
        // Given
        Alert alert2 = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("알림 2")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/page2")
                .site(testSite)
                .build();
        alertRepository.save(alert2);

        assertThat(alertRepository.count()).isEqualTo(2);

        // When
        int deletedCount = alertService.deleteAllAlerts();

        // Then
        assertThat(deletedCount).isEqualTo(2);
        assertThat(alertRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("알림이 없을 때 전체 삭제 시 0 반환")
    void deleteAllAlerts_NoAlerts_ReturnsZero() {
        // Given
        alertRepository.deleteAll();
        assertThat(alertRepository.count()).isEqualTo(0);

        // When
        int deletedCount = alertService.deleteAllAlerts();

        // Then
        assertThat(deletedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Alert 우선순위 자동 설정 - PRODUCT_RESTOCK은 HIGH")
    void alertPriority_ProductRestock_IsHigh() {
        // Given
        Alert restockAlert = Alert.builder()
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message("재입고")
                .pageTitle("제품")
                .detectedUrl("https://test.com/product")
                .product(testProduct)
                .build();

        // When
        Alert saved = alertRepository.save(restockAlert);

        // Then
        assertThat(saved.getPriority()).isEqualTo(Alert.Priority.HIGH);
    }

    @Test
    @DisplayName("Alert 우선순위 자동 설정 - KEYWORD는 NORMAL")
    void alertPriority_Keyword_IsNormal() {
        // Given
        Alert keywordAlert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("키워드")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/page")
                .site(testSite)
                .keyword(testKeyword)
                .build();

        // When
        Alert saved = alertRepository.save(keywordAlert);

        // Then
        assertThat(saved.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    @DisplayName("Alert 우선순위 자동 설정 - CONTENT_CHANGE는 NORMAL")
    void alertPriority_ContentChange_IsNormal() {
        // Given
        Alert contentChangeAlert = Alert.builder()
                .alertType(Alert.AlertType.CONTENT_CHANGE)
                .message("내용 변경")
                .pageTitle("페이지")
                .detectedUrl("https://test.com/changed")
                .site(testSite)
                .build();

        // When
        Alert saved = alertRepository.save(contentChangeAlert);

        // Then
        assertThat(saved.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    @DisplayName("재시도 가능 여부 확인 - 재시도 횟수가 MAX_RETRIES 미만")
    void canRetry_BelowMaxRetries_ReturnsTrue() {
        // Given
        testAlert.setRetryCount(2); // MAX_RETRIES = 3
        alertRepository.save(testAlert);

        // When
        Alert found = alertRepository.findById(testAlert.getId()).orElseThrow();

        // Then
        assertThat(found.canRetry()).isTrue();
    }

    @Test
    @DisplayName("재시도 불가능 - 재시도 횟수가 MAX_RETRIES 도달")
    void canRetry_AtMaxRetries_ReturnsFalse() {
        // Given
        testAlert.setRetryCount(3); // MAX_RETRIES = 3
        alertRepository.save(testAlert);

        // When
        Alert found = alertRepository.findById(testAlert.getId()).orElseThrow();

        // Then
        assertThat(found.canRetry()).isFalse();
    }

    @Test
    @DisplayName("재시도 횟수 증가")
    void incrementRetryCount_IncreasesCount() {
        // Given
        assertThat(testAlert.getRetryCount()).isEqualTo(0);

        // When
        testAlert.incrementRetryCount();
        alertRepository.save(testAlert);

        // Then
        Alert found = alertRepository.findById(testAlert.getId()).orElseThrow();
        assertThat(found.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cleanupAllExcessAlerts - 초과 사이트 알림 정리")
    void cleanupAllExcessAlerts_removesOldestAlertsOverLimit() {
        // Given: setUp의 testAlert(1개) + 3개 추가 = 총 4개, max=2
        for (int i = 0; i < 3; i++) {
            Alert a = Alert.builder()
                    .site(testSite)
                    .keyword(testKeyword)
                    .alertType(Alert.AlertType.KEYWORD)
                    .message("알림 " + i)
                    .detectedUrl("https://test.com/page/" + i)
                    .build();
            alertRepository.save(a);
        }
        assertThat(alertRepository.countBySite(testSite)).isEqualTo(4);

        // When
        alertService.cleanupAllExcessAlerts(2);

        // Then
        assertThat(alertRepository.countBySite(testSite)).isEqualTo(2);
    }

    @Test
    @DisplayName("cleanupAllExcessAlerts - 초과 없으면 삭제 안 함")
    void cleanupAllExcessAlerts_noExcess_noDelete() {
        // Given: setUp의 testAlert(1개) + 1개 추가 = 총 2개, max=3 → 초과 없음
        alertRepository.save(Alert.builder()
                .site(testSite)
                .keyword(testKeyword)
                .alertType(Alert.AlertType.KEYWORD)
                .message("알림")
                .detectedUrl("https://test.com/page/extra")
                .build());
        assertThat(alertRepository.countBySite(testSite)).isEqualTo(2);

        // When
        alertService.cleanupAllExcessAlerts(3);

        // Then
        assertThat(alertRepository.countBySite(testSite)).isEqualTo(2);
    }
}
