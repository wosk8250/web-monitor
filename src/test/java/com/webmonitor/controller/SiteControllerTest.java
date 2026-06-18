package com.webmonitor.controller;

import com.webmonitor.domain.Site;
import com.webmonitor.dto.SiteRequest;
import com.webmonitor.dto.SiteResponse;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.ArticleRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("SiteController 통합 테스트")
class SiteControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private ArticleRepository articleRepository;

    private Site testSite;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        keywordRepository.deleteAll();
        articleRepository.deleteAll();
        siteRepository.deleteAll();

        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .build();
        testSite = siteRepository.save(testSite);
    }

    @Test
    @DisplayName("GET /api/sites - 전체 사이트 조회: 200 반환")
    void getAllSites_returns200() {
        ResponseEntity<SiteResponse[]> response = restTemplate.getForEntity(
                "/api/sites", SiteResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("GET /api/sites?active=true - 활성 사이트만 반환")
    void getSitesByActiveTrue_returnsOnlyActiveSites() {
        Site inactive = Site.builder().name("비활성").url("https://inactive.com").active(false).build();
        siteRepository.save(inactive);

        ResponseEntity<SiteResponse[]> response = restTemplate.getForEntity(
                "/api/sites?active=true", SiteResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).allMatch(SiteResponse::getActive);
    }

    @Test
    @DisplayName("GET /api/sites/{id} - 존재하는 ID: 200 반환")
    void getSiteById_existingId_returns200() {
        ResponseEntity<SiteResponse> response = restTemplate.getForEntity(
                "/api/sites/" + testSite.getId(), SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testSite.getId());
    }

    @Test
    @DisplayName("GET /api/sites/{id} - 존재하지 않는 ID: 404 반환")
    void getSiteById_nonExistentId_returns404() {
        ResponseEntity<SiteResponse> response = restTemplate.getForEntity(
                "/api/sites/99999", SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/sites?name=테스트 - 이름 검색: 매칭된 결과 반환")
    void searchSites_matchingName_returnsResults() {
        ResponseEntity<SiteResponse[]> response = restTemplate.getForEntity(
                "/api/sites?name=테스트", SiteResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSizeGreaterThan(0);
    }

    @Test
    @DisplayName("GET /api/sites?name=<script> - XSS 페이로드: 200 반환, 빈 결과")
    void searchSites_xssPayload_returns200WithEmptyResults() {
        ResponseEntity<SiteResponse[]> response = restTemplate.getForEntity(
                "/api/sites?name=<script>alert(1)</script>", SiteResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/sites - 유효한 요청: 201 반환")
    void createSite_validRequest_returns201() {
        siteRepository.deleteAll();

        SiteRequest request = SiteRequest.builder()
                .name("새 사이트")
                .url("https://newsite.com")
                .active(true)
                .build();

        ResponseEntity<SiteResponse> response = restTemplate.postForEntity(
                "/api/sites", request, SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("새 사이트");
    }

    @Test
    @DisplayName("POST /api/sites - 이름 누락: 4xx 반환")
    void createSite_missingName_returnsClientError() {
        SiteRequest request = SiteRequest.builder()
                .url("https://newsite.com")
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/sites", request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - 사이트 수정: 200 반환 및 값 반영")
    void updateSite_validRequest_returns200() {
        SiteRequest update = SiteRequest.builder()
                .name("수정된 사이트")
                .url("https://updated.com")
                .active(true)
                .build();

        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/" + testSite.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(update),
                SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("수정된 사이트");
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - 존재하지 않는 ID: 404 반환")
    void updateSite_nonExistentId_returns404() {
        SiteRequest update = SiteRequest.builder()
                .name("수정")
                .url("https://updated.com")
                .build();

        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/99999",
                HttpMethod.PATCH,
                new HttpEntity<>(update),
                SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/sites/{id} - 사이트 삭제: 204 반환")
    void deleteSite_returns204() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/sites/" + testSite.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(siteRepository.existsById(testSite.getId())).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/sites/{id} - 존재하지 않는 ID: 404 반환")
    void deleteSite_nonExistentId_returns404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/sites/99999",
                HttpMethod.DELETE,
                null,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - active=false로 비활성화: 200 반환, 상태 변경 확인")
    void patchSiteActive_setFalse_returns200WithInactiveState() {
        SiteRequest patch = SiteRequest.builder().active(false).build();

        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/" + testSite.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getActive()).isFalse();
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - active 명시적 설정: 최종 상태 반영")
    void patchSiteActive_setThenUnset_returnsExpectedState() {
        SiteRequest patchFalse = SiteRequest.builder().active(false).build();
        restTemplate.exchange("/api/sites/" + testSite.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patchFalse), SiteResponse.class);

        SiteRequest patchTrue = SiteRequest.builder().active(true).build();
        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/" + testSite.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patchTrue), SiteResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getActive()).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - 존재하지 않는 ID에 active 변경: 404 반환")
    void patchSiteActive_nonExistentId_returns404() {
        SiteRequest patch = SiteRequest.builder().active(false).build();

        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/99999",
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/sites?name=&active= - 두 파라미터 동시 사용: 400 반환")
    void getSites_nameAndActiveTogether_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/sites?name=테스트&active=true", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/sites/{siteId}/alerts - 존재하지 않는 siteId: 404 반환")
    void getAlertsBySite_nonExistentSiteId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/sites/99999/alerts", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/sites/{siteId}/keywords - 존재하지 않는 siteId: 404 반환")
    void getKeywordsBySite_nonExistentSiteId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/sites/99999/keywords", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - name 빈 문자열: 기존 name 유지")
    void patchSite_emptyName_doesNotUpdateName() {
        SiteRequest patch = SiteRequest.builder().name("").build();

        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/" + testSite.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                SiteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(testSite.getName());
    }

    @Test
    @DisplayName("PATCH /api/sites/{id} - active만 변경 시 name/url 유지")
    void patchSiteActive_onlyActive_preservesNameAndUrl() {
        SiteRequest patch = SiteRequest.builder().active(false).build();

        ResponseEntity<SiteResponse> response = restTemplate.exchange(
                "/api/sites/" + testSite.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                SiteResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(testSite.getName());
        assertThat(response.getBody().getUrl()).isEqualTo(testSite.getUrl());
        assertThat(response.getBody().getActive()).isFalse();
    }
}
