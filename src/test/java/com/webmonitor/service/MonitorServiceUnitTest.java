package com.webmonitor.service;

import com.webmonitor.domain.Site;
import com.webmonitor.repository.SiteRepository;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MonitorService 단위 테스트")
class MonitorServiceUnitTest {

    @Mock private SiteRepository siteRepository;
    @Mock private WebCrawlerService webCrawlerService;
    @Mock private MonitorTransactionHandler monitorTransactionHandler;

    @Spy
    @InjectMocks
    private MonitorService monitorService;

    @BeforeEach
    void setUp() {
        // 동기 executor 주입 — 비동기 dispatch를 테스트에서 즉시 실행
        Executor syncExecutor = Runnable::run;
        ReflectionTestUtils.setField(monitorService, "siteMonitorExecutor", syncExecutor);
        doNothing().when(monitorService).monitorSite(any(Site.class));
    }

    // ========== monitorAllActiveSites 테스트 ==========

    @Test
    @DisplayName("monitorAllActiveSites - 체크 주기 경과 사이트: monitorSite 호출")
    void monitorAllActiveSites_eligibleSite_callsMonitorSite() {
        Site site = buildSite(null);  // lastCheckedAt=null → 항상 체크
        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(site));

        monitorService.monitorAllActiveSites();

        verify(monitorService, times(1)).monitorSite(site);
    }

    @Test
    @DisplayName("monitorAllActiveSites - 체크 주기 미경과 사이트: monitorSite 미호출")
    void monitorAllActiveSites_notEligible_skipsMonitorSite() {
        Site site = buildSite(LocalDateTime.now());  // 방금 체크됨
        site.setCheckIntervalMinutes(10);
        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(site));

        monitorService.monitorAllActiveSites();

        verify(monitorService, never()).monitorSite(any());
    }

    @Test
    @DisplayName("monitorAllActiveSites - 여러 사이트 중 일부만 경과: 해당 사이트만 dispatch")
    void monitorAllActiveSites_mixedSites_onlyDispatchsEligible() {
        Site eligible   = buildSite(null);
        Site ineligible = buildSite(LocalDateTime.now());
        ineligible.setCheckIntervalMinutes(10);

        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(eligible, ineligible));

        monitorService.monitorAllActiveSites();

        verify(monitorService, times(1)).monitorSite(eligible);
        verify(monitorService, never()).monitorSite(ineligible);
    }

    @Test
    @DisplayName("monitorAllActiveSites - 빈 사이트 목록: monitorSite 미호출")
    void monitorAllActiveSites_emptySites_noMonitoring() {
        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of());

        monitorService.monitorAllActiveSites();

        verify(monitorService, never()).monitorSite(any());
    }

    @Test
    @DisplayName("monitorAllActiveSites - monitorSite 예외 발생: 다른 사이트 계속 처리")
    void monitorAllActiveSites_monitorSiteThrows_continuesOtherSites() {
        Site site1 = buildSite(null);
        Site site2 = buildSite(null);

        doThrow(new RuntimeException("크롤링 실패")).when(monitorService).monitorSite(site1);

        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(site1, site2));

        monitorService.monitorAllActiveSites();

        verify(monitorService, times(1)).monitorSite(site1);
        verify(monitorService, times(1)).monitorSite(site2);
    }

    // ========== inProgressSites 중복 dispatch 방지 테스트 ==========

    @Test
    @DisplayName("monitorAllActiveSites - 이미 진행 중인 사이트: monitorSite 미호출")
    @SuppressWarnings("unchecked")
    void monitorAllActiveSites_siteAlreadyInProgress_skipsDispatch() {
        Site site = buildSite(null);
        Long siteId = site.getId();
        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(site));

        // 진행 중 상태 직접 설정
        ConcurrentHashMap<Long, Boolean> inProgressSites =
                (ConcurrentHashMap<Long, Boolean>) ReflectionTestUtils.getField(monitorService, "inProgressSites");
        inProgressSites.put(siteId, Boolean.TRUE);

        monitorService.monitorAllActiveSites();

        verify(monitorService, never()).monitorSite(any());
    }

    @Test
    @DisplayName("monitorAllActiveSites - 모니터링 완료 후 inProgressSites에서 제거")
    @SuppressWarnings("unchecked")
    void monitorAllActiveSites_afterCompletion_removedFromInProgress() {
        Site site = buildSite(null);
        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(site));

        monitorService.monitorAllActiveSites();

        ConcurrentHashMap<Long, Boolean> inProgressSites =
                (ConcurrentHashMap<Long, Boolean>) ReflectionTestUtils.getField(monitorService, "inProgressSites");
        assertThat(inProgressSites).doesNotContainKey(site.getId());
    }

    @Test
    @DisplayName("monitorAllActiveSites - 예외 발생 후에도 inProgressSites에서 제거")
    @SuppressWarnings("unchecked")
    void monitorAllActiveSites_monitorSiteThrows_stillRemovedFromInProgress() {
        Site site = buildSite(null);
        when(siteRepository.findByActiveWithKeywords(true)).thenReturn(List.of(site));
        doThrow(new RuntimeException("크롤링 실패")).when(monitorService).monitorSite(site);

        monitorService.monitorAllActiveSites();

        ConcurrentHashMap<Long, Boolean> inProgressSites =
                (ConcurrentHashMap<Long, Boolean>) ReflectionTestUtils.getField(monitorService, "inProgressSites");
        assertThat(inProgressSites).doesNotContainKey(site.getId());
    }

    // ========== Helpers ==========

    private long nextId = 1L;

    private Site buildSite(LocalDateTime lastCheckedAt) {
        Site site = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .checkIntervalMinutes(1)
                .build();
        ReflectionTestUtils.setField(site, "id", nextId++);
        site.setLastCheckedAt(lastCheckedAt);
        return site;
    }
}
