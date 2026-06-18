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
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * MonitorService 통합 테스트
 */
@SpringBootTest
class MonitorServiceTest {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @MockBean
    private SseService sseService;

    private Site testSite;
    private Keyword testKeyword;

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
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);

        // 테스트용 키워드 생성 및 저장
        testKeyword = Keyword.builder()
                .keyword("긴급")
                .site(testSite)
                .active(true)
                .build();
        testKeyword = keywordRepository.save(testKeyword);
    }

    @Test
    @DisplayName("키워드 감지 알림 생성 - 메시지 형식 확인 (글 제목 있음)")
    void createAlert_WithPageTitle() {
        // Given
        String pageTitle = "중요한 공지사항";
        String detectedUrl = "https://test.com/notice/123";

        // When
        monitorService.createAlert(testSite, testKeyword, pageTitle, detectedUrl);

        // Then
        Alert savedAlert = alertRepository.findAll().get(0);
        assertThat(savedAlert).isNotNull();
        assertThat(savedAlert.getMessage())
                .isEqualTo("[테스트 사이트] 키워드 '긴급' 감지 - 중요한 공지사항");
        assertThat(savedAlert.getPageTitle()).isEqualTo(pageTitle);
        assertThat(savedAlert.getDetectedUrl()).isEqualTo(detectedUrl);
        assertThat(savedAlert.getSent()).isFalse();

        // SSE 전송 확인
        verify(sseService).broadcastAlert(any(Alert.class));
    }

    @Test
    @DisplayName("키워드 감지 알림 생성 - 메시지 형식 확인 (글 제목 없음)")
    void createAlert_WithoutPageTitle() {
        // Given
        String pageTitle = null;
        String detectedUrl = "https://test.com/page";

        // When
        monitorService.createAlert(testSite, testKeyword, pageTitle, detectedUrl);

        // Then
        Alert savedAlert = alertRepository.findAll().get(0);
        assertThat(savedAlert).isNotNull();
        assertThat(savedAlert.getMessage())
                .isEqualTo("[테스트 사이트] 키워드 '긴급' 감지 - 제목 없음");
        assertThat(savedAlert.getPageTitle()).isNull();
    }

    @Test
    @DisplayName("키워드 감지 알림 생성 - 메시지 형식 확인 (빈 제목)")
    void createAlert_WithEmptyPageTitle() {
        // Given
        String pageTitle = "";
        String detectedUrl = "https://test.com/page";

        // When
        monitorService.createAlert(testSite, testKeyword, pageTitle, detectedUrl);

        // Then
        Alert savedAlert = alertRepository.findAll().get(0);
        assertThat(savedAlert).isNotNull();
        assertThat(savedAlert.getMessage())
                .isEqualTo("[테스트 사이트] 키워드 '긴급' 감지 - 제목 없음");
    }

    @Test
    @DisplayName("내용 변경 알림 생성 - 메시지 형식 확인 (글 제목 있음)")
    void createContentChangeAlert_WithPageTitle() {
        // Given
        String pageTitle = "새로운 게시글";
        String detectedUrl = "https://test.com/new-post";

        // When
        monitorService.createContentChangeAlert(testSite, pageTitle, detectedUrl);

        // Then
        Alert savedAlert = alertRepository.findAll().get(0);
        assertThat(savedAlert).isNotNull();
        assertThat(savedAlert.getMessage())
                .isEqualTo("[테스트 사이트] 새 글 - 새로운 게시글");
        assertThat(savedAlert.getPageTitle()).isEqualTo(pageTitle);
        assertThat(savedAlert.getDetectedUrl()).isEqualTo(detectedUrl);
        assertThat(savedAlert.getKeyword()).isNull(); // 전체 페이지 변경은 키워드 없음
        assertThat(savedAlert.getSent()).isFalse();

        // SSE 전송 확인
        verify(sseService).broadcastAlert(any(Alert.class));
    }

    @Test
    @DisplayName("내용 변경 알림 생성 - 메시지 형식 확인 (글 제목 없음)")
    void createContentChangeAlert_WithoutPageTitle() {
        // Given
        String pageTitle = null;
        String detectedUrl = "https://test.com/page";

        // When
        monitorService.createContentChangeAlert(testSite, pageTitle, detectedUrl);

        // Then
        Alert savedAlert = alertRepository.findAll().get(0);
        assertThat(savedAlert).isNotNull();
        assertThat(savedAlert.getMessage())
                .isEqualTo("[테스트 사이트] 새 글 - 제목 없음");
        assertThat(savedAlert.getKeyword()).isNull();
    }

    @Test
    @DisplayName("내용 변경 알림 생성 - 메시지 형식 확인 (빈 제목)")
    void createContentChangeAlert_WithEmptyPageTitle() {
        // Given
        String pageTitle = "   "; // 공백만
        String detectedUrl = "https://test.com/page";

        // When
        monitorService.createContentChangeAlert(testSite, pageTitle, detectedUrl);

        // Then
        Alert savedAlert = alertRepository.findAll().get(0);
        assertThat(savedAlert).isNotNull();
        // 공백 제거 후 빈 문자열이므로 "제목 없음"으로 처리
        assertThat(savedAlert.getMessage())
                .isEqualTo("[테스트 사이트] 새 글 - 제목 없음");
    }

    @Test
    @DisplayName("사이트 해시값 초기화")
    void resetSiteHash() {
        // When
        monitorService.resetSiteHash(testSite.getId());

        // Then - 예외 없이 정상 실행됨을 확인
        // 해시맵 내부 상태는 확인할 수 없지만 메서드 실행 확인
    }

    @Test
    @DisplayName("모든 사이트 해시값 초기화")
    void resetAllHashes() {
        // When
        monitorService.resetAllHashes();

        // Then - 예외 없이 정상 실행됨을 확인
    }
}
