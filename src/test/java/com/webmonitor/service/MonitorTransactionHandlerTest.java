package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.ArticleRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MonitorTransactionHandler 단위 테스트")
class MonitorTransactionHandlerTest {

    @Mock private SiteRepository siteRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private KeywordRepository keywordRepository;
    @Mock private AlertService alertService;
    @Mock private SseService sseService;
    @Mock private ArticleExtractorService articleExtractorService;
    @Mock private ArticleSaverService articleSaverService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy
    @InjectMocks
    private MonitorTransactionHandler handler;

    private Site testSite;
    private Keyword testKeyword;
    private Alert mockAlert;

    @BeforeEach
    void setUp() {
        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .build();
        ReflectionTestUtils.setField(testSite, "id", 1L);

        testKeyword = Keyword.builder()
                .keyword("긴급")
                .site(testSite)
                .active(true)
                .build();
        ReflectionTestUtils.setField(testKeyword, "id", 1L);

        mockAlert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("테스트 알림")
                .detectedUrl("https://test.com/page")
                .build();
        ReflectionTestUtils.setField(mockAlert, "id", 10L);

        when(alertService.createAlert(any())).thenReturn(mockAlert);
    }

    // ========== detectKeywords 테스트 ==========

    @Test
    @DisplayName("detectKeywords - 사이트 키워드 포함: createAlert 호출")
    void detectKeywords_pageContainsSiteKeyword_callsCreateAlert() {
        when(keywordRepository.findBySiteAndActive(testSite, true)).thenReturn(List.of(testKeyword));
        when(keywordRepository.findBySiteIsNullAndActive(true)).thenReturn(List.of());

        handler.detectKeywords(testSite, "긴급 공지사항입니다", "제목", "https://test.com/page");

        verify(alertService, times(1)).createAlert(any());
    }

    @Test
    @DisplayName("detectKeywords - 글로벌 키워드 포함: createAlert 호출")
    void detectKeywords_pageContainsGlobalKeyword_callsCreateAlert() {
        Keyword globalKeyword = Keyword.builder().keyword("속보").active(true).build();
        when(keywordRepository.findBySiteAndActive(testSite, true)).thenReturn(List.of());
        when(keywordRepository.findBySiteIsNullAndActive(true)).thenReturn(List.of(globalKeyword));

        handler.detectKeywords(testSite, "속보 뉴스가 발생했습니다", "제목", "https://test.com/page");

        verify(alertService, times(1)).createAlert(any());
    }

    @Test
    @DisplayName("detectKeywords - 키워드 미포함: createAlert 미호출")
    void detectKeywords_noKeywordMatch_noAlert() {
        when(keywordRepository.findBySiteAndActive(testSite, true)).thenReturn(List.of(testKeyword));
        when(keywordRepository.findBySiteIsNullAndActive(true)).thenReturn(List.of());

        handler.detectKeywords(testSite, "일반 내용입니다", "제목", "https://test.com/page");

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("detectKeywords - 등록된 키워드 없음: createAlert 미호출")
    void detectKeywords_noKeywordsRegistered_noAlert() {
        when(keywordRepository.findBySiteAndActive(testSite, true)).thenReturn(List.of());
        when(keywordRepository.findBySiteIsNullAndActive(true)).thenReturn(List.of());

        handler.detectKeywords(testSite, "페이지 내용", "제목", "https://test.com/page");

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("detectKeywords - 여러 키워드 모두 포함: 각 키워드마다 createAlert 호출")
    void detectKeywords_multipleKeywordsAllMatch_callsAlertForEach() {
        Keyword keyword2 = Keyword.builder().keyword("마감").site(testSite).active(true).build();
        when(keywordRepository.findBySiteAndActive(testSite, true)).thenReturn(List.of(testKeyword, keyword2));
        when(keywordRepository.findBySiteIsNullAndActive(true)).thenReturn(List.of());

        handler.detectKeywords(testSite, "긴급 마감 안내", "제목", "https://test.com/page");

        verify(alertService, times(2)).createAlert(any());
    }

    // ========== createAlert 테스트 ==========

    @Test
    @DisplayName("createAlert - 정상 케이스: 알림 저장 + SSE 브로드캐스트")
    void createAlert_happyPath_savesAlertAndBroadcasts() {
        handler.createAlert(testSite, testKeyword, "공지사항", "https://test.com/notice");

        verify(alertService, times(1)).createAlert(any());
        verify(sseService, times(1)).broadcastAlert(mockAlert);
    }

    @Test
    @DisplayName("createAlert - pageTitle null: 메시지에 '제목 없음' 포함")
    void createAlert_nullPageTitle_usesDefaultTitle() {
        handler.createAlert(testSite, testKeyword, null, "https://test.com/notice");

        verify(alertService, times(1)).createAlert(argThat(alert ->
                alert.getMessage().contains("제목 없음")));
    }

    @Test
    @DisplayName("createAlert - pageTitle 공백: 메시지에 '제목 없음' 포함")
    void createAlert_blankPageTitle_usesDefaultTitle() {
        handler.createAlert(testSite, testKeyword, "  ", "https://test.com/notice");

        verify(alertService, times(1)).createAlert(argThat(alert ->
                alert.getMessage().contains("제목 없음")));
    }

    @Test
    @DisplayName("createAlert - SSE 브로드캐스트 실패: 이벤트 발행 후 예외 전파 안 됨")
    void createAlert_sseBroadcastFails_publishesEventNoPropagation() {
        doThrow(new RuntimeException("SSE 실패")).when(sseService).broadcastAlert(any());

        handler.createAlert(testSite, testKeyword, "공지사항", "https://test.com/notice");

        verify(alertService, times(1)).createAlert(any());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    // ========== createContentChangeAlert 테스트 ==========

    @Test
    @DisplayName("createContentChangeAlert - 정상 케이스: keyword=null 알림 저장 + SSE 브로드캐스트")
    void createContentChangeAlert_happyPath_savesAlertWithNullKeyword() {
        handler.createContentChangeAlert(testSite, "새로운 게시글", "https://test.com/post");

        verify(alertService, times(1)).createAlert(argThat(alert ->
                alert.getKeyword() == null &&
                alert.getMessage().contains("새로운 게시글")));
        verify(sseService, times(1)).broadcastAlert(mockAlert);
    }

    @Test
    @DisplayName("createContentChangeAlert - pageTitle null: 메시지에 '제목 없음' 포함")
    void createContentChangeAlert_nullPageTitle_usesDefaultTitle() {
        handler.createContentChangeAlert(testSite, null, "https://test.com/post");

        verify(alertService, times(1)).createAlert(argThat(alert ->
                alert.getMessage().contains("제목 없음")));
    }

    @Test
    @DisplayName("createContentChangeAlert - SSE 브로드캐스트 실패: 이벤트 발행 후 예외 전파 안 됨")
    void createContentChangeAlert_sseBroadcastFails_publishesEventNoPropagation() {
        doThrow(new RuntimeException("SSE 실패")).when(sseService).broadcastAlert(any());

        handler.createContentChangeAlert(testSite, "게시글", "https://test.com/post");

        verify(alertService, times(1)).createAlert(any());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    // ========== extractAndProcessArticles — DataIntegrityViolationException 처리 ==========

    @Test
    @DisplayName("processSiteMonitoringResult - article 중복 감지(saveIfNotDuplicate=false): 예외 없이 해당 article 스킵")
    void processSiteMonitoringResult_articleDuplicateInsert_continuesWithoutException() {
        // Given: 최초 모니터링 사이트 (lastContentHash=null → isFirstRun=true)
        Site managedSite = Site.builder()
                .name("테스트 사이트").url("https://test.com").active(true).build();
        ReflectionTestUtils.setField(managedSite, "id", 1L);
        when(siteRepository.findById(1L)).thenReturn(Optional.of(managedSite));

        // article 링크 1개 반환 (base URI 포함 → absUrl 정상 동작)
        Document doc = Jsoup.parse("<a href='/article/1'>Article Title</a>", "https://test.com");
        Elements links = doc.select("a");
        when(articleExtractorService.extractArticleLinks(any(), any())).thenReturn(links);
        when(articleExtractorService.extractArticleId(anyString())).thenReturn("1");
        when(articleRepository.existsBySiteAndArticleId(any(), anyString())).thenReturn(false);

        // saveIfNotDuplicate가 false를 반환 → 중복으로 간주
        when(articleSaverService.saveIfNotDuplicate(any())).thenReturn(false);

        // When & Then: 예외 전파 없이 완료
        assertThatCode(() ->
                handler.processSiteMonitoringResult(managedSite, doc, "제목", "내용", "hash123"))
                .doesNotThrowAnyException();

        // 중복 article → processArticleForKeywords 스킵 → createAlert 미호출
        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("processSiteMonitoringResult - article 정상 저장(saveIfNotDuplicate=true): 키워드 검사 진행")
    void processSiteMonitoringResult_articleSavedSuccessfully_processesKeywords() {
        // Given: 최초 모니터링 사이트
        Site managedSite = Site.builder()
                .name("테스트 사이트").url("https://test.com").active(true).build();
        ReflectionTestUtils.setField(managedSite, "id", 1L);
        when(siteRepository.findById(1L)).thenReturn(Optional.of(managedSite));

        Document doc = Jsoup.parse("<a href='/article/1'>Article Title</a>", "https://test.com");
        when(articleExtractorService.extractArticleLinks(any(), any())).thenReturn(doc.select("a"));
        when(articleExtractorService.extractArticleId(anyString())).thenReturn("1");
        when(articleRepository.existsBySiteAndArticleId(any(), anyString())).thenReturn(false);
        when(articleSaverService.saveIfNotDuplicate(any())).thenReturn(true);

        // When & Then: 예외 없이 완료
        assertThatCode(() ->
                handler.processSiteMonitoringResult(managedSite, doc, "제목", "내용", "hash123"))
                .doesNotThrowAnyException();

        verify(articleSaverService, times(1)).saveIfNotDuplicate(any());
    }
}
