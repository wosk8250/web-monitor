package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 웹사이트 모니터링 및 키워드 감지를 처리하는 서비스
 */
@Service // Spring의 Service 컴포넌트로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class MonitorService {

    private final SiteRepository siteRepository;
    private final KeywordRepository keywordRepository;
    private final AlertRepository alertRepository;
    private final SseService sseService; // SSE 실시간 알림 서비스

    // 사이트별 이전 해시값을 메모리에 저장 (실제 운영환경에서는 DB나 Redis 사용 권장)
    private final Map<Long, String> siteContentHashMap = new HashMap<>();

    /**
     * 모든 활성화된 사이트 모니터링 실행
     */
    @Transactional
    public void monitorAllActiveSites() {
        log.info("활성화된 사이트 모니터링 시작");

        List<Site> activeSites = siteRepository.findByActive(true);
        log.info("모니터링할 사이트 수: {}", activeSites.size());

        for (Site site : activeSites) {
            try {
                monitorSite(site);
            } catch (Exception e) {
                log.error("사이트 모니터링 중 오류 발생: {} - {}", site.getName(), e.getMessage(), e);
            }
        }

        log.info("모니터링 작업 완료");
    }

    /**
     * 특정 사이트 모니터링
     * @param site 모니터링할 사이트
     */
    @Transactional
    public void monitorSite(Site site) {
        log.info("사이트 모니터링 시작: {}", site.getName());

        try {
            // JSoup을 사용하여 웹페이지 크롤링
            Document document = crawlWebsite(site.getUrl());

            // 불필요한 태그 제거 (스크립트, 스타일, 네비게이션 요소 등)
            document.select("script, style, header, footer, nav, aside").remove();

            // 본문 내용만 텍스트 추출
            String pageText = document.body().text();

            // 해시값 계산 및 변경 감지
            String currentHash = calculateHash(pageText);
            boolean contentChanged = detectContentChange(site.getId(), currentHash);

            if (contentChanged) {
                log.info("사이트 내용 변경 감지: {}", site.getName());

                // 전체 페이지 변경 감지 옵션이 활성화된 경우 알림 생성
                if (site.getDetectContentChange()) {
                    createContentChangeAlert(site, document.location());
                }
            }

            // 키워드 감지
            detectKeywords(site, pageText, document.location());

        } catch (IOException e) {
            log.error("사이트 크롤링 실패: {} - {}", site.getName(), e.getMessage());
        } catch (Exception e) {
            log.error("모니터링 중 예외 발생: {} - {}", site.getName(), e.getMessage(), e);
        }
    }

    /**
     * JSoup을 사용하여 웹사이트 크롤링
     * @param url 크롤링할 URL
     * @return JSoup Document 객체
     * @throws IOException 크롤링 실패 시
     */
    private Document crawlWebsite(String url) throws IOException {
        log.debug("웹사이트 크롤링: {}", url);

        // JSoup으로 웹페이지 가져오기
        // timeout: 10초, userAgent: 일반 브라우저로 위장
        Document document = Jsoup.connect(url)
                .timeout(10000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get();

        log.debug("크롤링 완료: {}", url);
        return document;
    }

    /**
     * 문자열의 SHA-256 해시값 계산
     * @param content 해시를 계산할 문자열
     * @return 16진수 문자열로 변환된 해시값
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("해시 알고리즘을 찾을 수 없습니다: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 사이트 내용 변경 감지
     * @param siteId 사이트 ID
     * @param currentHash 현재 해시값
     * @return 변경 여부 (true: 변경됨, false: 변경 안됨)
     */
    private boolean detectContentChange(Long siteId, String currentHash) {
        String previousHash = siteContentHashMap.get(siteId);

        // 이전 해시값이 없으면 최초 실행
        if (previousHash == null) {
            log.debug("최초 모니터링 - 해시값 저장: Site ID = {}", siteId);
            siteContentHashMap.put(siteId, currentHash);
            return false;
        }

        // 해시값 비교
        boolean changed = !previousHash.equals(currentHash);

        if (changed) {
            log.info("내용 변경 감지: Site ID = {}", siteId);
            siteContentHashMap.put(siteId, currentHash); // 새로운 해시값 저장
        }

        return changed;
    }

    /**
     * 페이지에서 키워드 감지
     * @param site 사이트 정보
     * @param pageText 페이지 전체 텍스트
     * @param detectedUrl 감지된 URL
     */
    @Transactional
    public void detectKeywords(Site site, String pageText, String detectedUrl) {
        // 해당 사이트의 활성화된 키워드 조회
        List<Keyword> keywords = keywordRepository.findBySiteAndActive(site, true);

        if (keywords.isEmpty()) {
            log.debug("사이트에 등록된 활성 키워드가 없습니다: {}", site.getName());
            return;
        }

        log.debug("키워드 감지 시작: {} (키워드 수: {})", site.getName(), keywords.size());

        // 각 키워드에 대해 검사
        for (Keyword keyword : keywords) {
            if (pageText.contains(keyword.getKeyword())) {
                log.info("키워드 감지! 사이트: {}, 키워드: {}", site.getName(), keyword.getKeyword());
                createAlert(site, keyword, pageText, detectedUrl);
            }
        }
    }

    /**
     * 알림 생성 및 실시간 전송
     * @param site 사이트 정보
     * @param keyword 감지된 키워드
     * @param pageText 페이지 텍스트
     * @param detectedUrl 감지된 URL
     */
    @Transactional
    public void createAlert(Site site, Keyword keyword, String pageText, String detectedUrl) {
        // 알림 메시지 생성
        String message = String.format(
                "[%s] 사이트에서 키워드 '%s'가 감지되었습니다.",
                site.getName(),
                keyword.getKeyword()
        );

        // Alert 엔티티 생성
        Alert alert = Alert.builder()
                .site(site)
                .keyword(keyword)
                .message(message)
                .detectedUrl(detectedUrl)
                .sent(false) // 아직 전송되지 않음
                .build();

        // 데이터베이스에 저장
        Alert savedAlert = alertRepository.save(alert);
        log.info("알림 생성 완료: {}", message);

        // SSE를 통해 모든 연결된 클라이언트에게 실시간 알림 전송
        // 디스코드 웹훅도 자동으로 전송됨
        try {
            sseService.broadcastAlert(savedAlert);
            log.info("실시간 알림 전송 완료: 알림 ID = {}", savedAlert.getId());
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 전체 페이지 변경 감지 알림 생성 및 실시간 전송
     * @param site 사이트 정보
     * @param detectedUrl 감지된 URL
     */
    @Transactional
    public void createContentChangeAlert(Site site, String detectedUrl) {
        // 알림 메시지 생성
        String message = String.format(
                "[%s] 사이트의 내용이 변경되었습니다.",
                site.getName()
        );

        // Alert 엔티티 생성 (키워드 없이)
        Alert alert = Alert.builder()
                .site(site)
                .keyword(null) // 전체 페이지 변경 감지는 키워드 없음
                .message(message)
                .detectedUrl(detectedUrl)
                .sent(false) // 아직 전송되지 않음
                .build();

        // 데이터베이스에 저장
        Alert savedAlert = alertRepository.save(alert);
        log.info("내용 변경 알림 생성 완료: {}", message);

        // SSE를 통해 모든 연결된 클라이언트에게 실시간 알림 전송
        // 디스코드 웹훅도 자동으로 전송됨
        try {
            sseService.broadcastAlert(savedAlert);
            log.info("실시간 알림 전송 완료: 알림 ID = {}", savedAlert.getId());
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 사이트의 저장된 해시값 초기화
     * @param siteId 사이트 ID
     */
    public void resetSiteHash(Long siteId) {
        siteContentHashMap.remove(siteId);
        log.info("사이트 해시값 초기화: Site ID = {}", siteId);
    }

    /**
     * 모든 사이트의 저장된 해시값 초기화
     */
    public void resetAllHashes() {
        siteContentHashMap.clear();
        log.info("모든 사이트 해시값 초기화");
    }
}
