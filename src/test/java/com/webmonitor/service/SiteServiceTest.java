package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SiteService 통합 테스트
 */
@SpringBootTest
@Transactional
class SiteServiceTest {

    @Autowired
    private SiteService siteService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private AlertRepository alertRepository;

    private Site testSite;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        alertRepository.deleteAll();
        keywordRepository.deleteAll();
        siteRepository.deleteAll();

        // 테스트용 사이트 생성 및 저장
        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .checkInterval(10)
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);
    }

    @Test
    @DisplayName("사이트 삭제 시 연관된 Alert도 함께 삭제 (Cascade)")
    void deleteSite_WithAlerts_CascadeDelete() {
        // Given
        Alert alert1 = Alert.builder()
                .site(testSite)
                .message("테스트 알림 1")
                .pageTitle("제목 1")
                .detectedUrl("https://test.com/1")
                .sent(false)
                .build();
        Alert alert2 = Alert.builder()
                .site(testSite)
                .message("테스트 알림 2")
                .pageTitle("제목 2")
                .detectedUrl("https://test.com/2")
                .sent(true)
                .build();

        alertRepository.save(alert1);
        alertRepository.save(alert2);

        // 사전 확인
        assertThat(siteRepository.count()).isEqualTo(1);
        assertThat(alertRepository.count()).isEqualTo(2);

        // When - Site 삭제
        siteService.deleteSite(testSite.getId());

        // Then - Site와 연관된 Alert도 함께 삭제됨
        assertThat(siteRepository.count()).isZero();
        assertThat(alertRepository.count()).isZero(); // Cascade로 Alert도 삭제
    }

    @Test
    @DisplayName("사이트 삭제 시 연관된 Keyword도 함께 삭제 (Cascade)")
    void deleteSite_WithKeywords_CascadeDelete() {
        // Given
        Keyword keyword1 = Keyword.builder()
                .keyword("긴급")
                .site(testSite)
                .active(true)
                .build();
        Keyword keyword2 = Keyword.builder()
                .keyword("공지")
                .site(testSite)
                .active(true)
                .build();

        keywordRepository.save(keyword1);
        keywordRepository.save(keyword2);

        // 사전 확인
        assertThat(siteRepository.count()).isEqualTo(1);
        assertThat(keywordRepository.count()).isEqualTo(2);

        // When - Site 삭제
        siteService.deleteSite(testSite.getId());

        // Then - Site와 연관된 Keyword도 함께 삭제됨
        assertThat(siteRepository.count()).isZero();
        assertThat(keywordRepository.count()).isZero(); // Cascade로 Keyword도 삭제
    }

    @Test
    @DisplayName("사이트 삭제 시 Alert와 Keyword 모두 함께 삭제")
    void deleteSite_WithAlertsAndKeywords_CascadeDelete() {
        // Given
        // Keyword 추가
        Keyword keyword = Keyword.builder()
                .keyword("긴급")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        // Alert 추가 (키워드 연결)
        Alert alert = Alert.builder()
                .site(testSite)
                .keyword(keyword)
                .message("키워드 감지 알림")
                .pageTitle("테스트 제목")
                .detectedUrl("https://test.com/page")
                .sent(false)
                .build();
        alertRepository.save(alert);

        // 사전 확인
        assertThat(siteRepository.count()).isEqualTo(1);
        assertThat(keywordRepository.count()).isEqualTo(1);
        assertThat(alertRepository.count()).isEqualTo(1);

        // When - Site 삭제
        siteService.deleteSite(testSite.getId());

        // Then - Site, Keyword, Alert 모두 삭제됨
        assertThat(siteRepository.count()).isZero();
        assertThat(keywordRepository.count()).isZero();
        assertThat(alertRepository.count()).isZero();
    }

    @Test
    @DisplayName("사이트 삭제 - 존재하지 않는 ID")
    void deleteSite_NotFound() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> siteService.deleteSite(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사이트를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("사이트 생성 - 정상 케이스")
    void createSite_Success() {
        // Given
        Site newSite = Site.builder()
                .name("새 사이트")
                .url("https://new-site.com")
                .checkInterval(5)
                .active(true)
                .detectContentChange(true)
                .build();

        // When
        Site createdSite = siteService.createSite(newSite);

        // Then
        assertThat(createdSite).isNotNull();
        assertThat(createdSite.getId()).isNotNull();
        assertThat(createdSite.getName()).isEqualTo("새 사이트");
        assertThat(siteRepository.count()).isEqualTo(2); // 기존 1개 + 새로 추가 1개
    }

    @Test
    @DisplayName("사이트 수정 - 정상 케이스")
    void updateSite_Success() {
        // Given
        Site updateData = Site.builder()
                .name("수정된 사이트")
                .url("https://updated.com")
                .checkInterval(15)
                .active(false)
                .detectContentChange(true)
                .build();

        // When
        Site updatedSite = siteService.updateSite(testSite.getId(), updateData);

        // Then
        assertThat(updatedSite.getName()).isEqualTo("수정된 사이트");
        assertThat(updatedSite.getUrl()).isEqualTo("https://updated.com");
        assertThat(updatedSite.getCheckInterval()).isEqualTo(15);
        assertThat(updatedSite.getActive()).isFalse();
    }

    @Test
    @DisplayName("사이트 수정 - 존재하지 않는 ID")
    void updateSite_NotFound() {
        // Given
        Long nonExistentId = 99999L;
        Site updateData = Site.builder()
                .name("수정할 데이터")
                .url("https://test.com")
                .checkInterval(10)
                .active(true)
                .build();

        // When & Then
        assertThatThrownBy(() -> siteService.updateSite(nonExistentId, updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사이트를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("사이트 활성화 토글 - 정상 케이스")
    void toggleSiteActive_Success() {
        // Given
        boolean initialActive = testSite.getActive();

        // When
        Site toggledSite = siteService.toggleSiteActive(testSite.getId());

        // Then
        assertThat(toggledSite.getActive()).isEqualTo(!initialActive);
    }

    @Test
    @DisplayName("사이트 활성화 토글 - 존재하지 않는 ID")
    void toggleSiteActive_NotFound() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> siteService.toggleSiteActive(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사이트를 찾을 수 없습니다");
    }
}
