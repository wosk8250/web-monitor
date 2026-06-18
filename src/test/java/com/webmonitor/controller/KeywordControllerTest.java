package com.webmonitor.controller;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.dto.KeywordResponse;
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
class KeywordControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private SiteRepository siteRepository;

    private Site testSite;

    @BeforeEach
    void setUp() {
        keywordRepository.deleteAll();
        siteRepository.deleteAll();

        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);
    }

    @Test
    @DisplayName("키워드 등록 - 정상 케이스 (siteId 지정)")
    void createKeyword_WithSiteId_Success() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("테스트 키워드")
                .siteId(testSite.getId())
                .active(true)
                .build();

        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getKeyword()).isEqualTo("테스트 키워드");
        assertThat(response.getBody().getSiteId()).isEqualTo(testSite.getId());
        assertThat(response.getBody().getSiteName()).isEqualTo("테스트 사이트");
        assertThat(response.getBody().getActive()).isTrue();

        assertThat(keywordRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드 등록 - 정상 케이스 (전체 공통 키워드, siteId null)")
    void createKeyword_WithoutSiteId_Success() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("공통 키워드")
                .siteId(null)
                .active(true)
                .build();

        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getKeyword()).isEqualTo("공통 키워드");
        assertThat(response.getBody().getSiteId()).isNull();
        assertThat(response.getBody().getSiteName()).isEqualTo("전체 공통");
        assertThat(response.getBody().getActive()).isTrue();

        Keyword savedKeyword = keywordRepository.findAll().get(0);
        assertThat(savedKeyword.getSite()).isNull();
    }

    @Test
    @DisplayName("공백 키워드 등록 → 새글 감지 자동 활성화")
    void createKeyword_WithEmptyKeyword_EnablesContentDetection() {
        testSite.setDetectContentChange(false);
        testSite = siteRepository.save(testSite);

        KeywordRequest request = KeywordRequest.builder()
                .keyword("")
                .siteId(testSite.getId())
                .active(true)
                .build();

        ResponseEntity<java.util.Map> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                java.util.Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("message")).isEqualTo("새글 감지 기능이 활성화되었습니다");

        Site updatedSite = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(updatedSite.getDetectContentChange()).isTrue();

        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("공백 키워드 등록 - 실패 (siteId null)")
    void createKeyword_WithEmptyKeywordAndNullSiteId_Fail() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("")
                .siteId(null)
                .active(true)
                .build();

        ResponseEntity<java.util.Map> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                java.util.Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("키워드 등록 - 실패 (keyword가 null)")
    void createKeyword_WithNullKeyword_Fail() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword(null)
                .siteId(testSite.getId())
                .active(true)
                .build();

        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("키워드 등록 - 실패 (존재하지 않는 siteId)")
    void createKeyword_WithNonExistentSiteId_Fail() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("테스트 키워드")
                .siteId(99999L)
                .active(true)
                .build();

        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("PATCH /api/keywords/{id} - 키워드 수정 정상 케이스")
    void updateKeyword_Success() {
        Keyword keyword = Keyword.builder()
                .keyword("원래 키워드")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        KeywordRequest updateRequest = KeywordRequest.builder()
                .keyword("수정된 키워드")
                .siteId(testSite.getId())
                .active(false)
                .build();

        ResponseEntity<KeywordResponse> response = restTemplate.exchange(
                "/api/keywords/" + keyword.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(updateRequest),
                KeywordResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Keyword updatedKeyword = keywordRepository.findById(keyword.getId()).orElseThrow();
        assertThat(updatedKeyword.getKeyword()).isEqualTo("수정된 키워드");
        assertThat(updatedKeyword.getActive()).isFalse();
    }

    @Test
    @DisplayName("키워드 삭제 - 정상 케이스")
    void deleteKeyword_Success() {
        Keyword keyword = Keyword.builder()
                .keyword("삭제될 키워드")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        restTemplate.delete("/api/keywords/" + keyword.getId());

        assertThat(keywordRepository.existsById(keyword.getId())).isFalse();
    }

    @Test
    @DisplayName("PATCH /api/keywords/{id} - active=false로 비활성화")
    void patchKeywordActive_setFalse_Success() {
        Keyword keyword = Keyword.builder()
                .keyword("토글 테스트")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        KeywordRequest patch = KeywordRequest.builder().active(false).build();
        ResponseEntity<KeywordResponse> response = restTemplate.exchange(
                "/api/keywords/" + keyword.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                KeywordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getActive()).isFalse();

        Keyword updated = keywordRepository.findById(keyword.getId()).orElseThrow();
        assertThat(updated.getActive()).isFalse();
    }

    @Test
    @DisplayName("GET /api/keywords?global=true - 전체 공통 키워드 조회")
    void getGlobalKeywords_Success() {
        Keyword globalKeyword = Keyword.builder()
                .keyword("전체 공통 키워드")
                .site(null)
                .active(true)
                .build();
        keywordRepository.save(globalKeyword);

        Keyword siteKeyword = Keyword.builder()
                .keyword("사이트 키워드")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(siteKeyword);

        ResponseEntity<KeywordResponse[]> response = restTemplate.getForEntity(
                "/api/keywords?global=true",
                KeywordResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getKeyword()).isEqualTo("전체 공통 키워드");
        assertThat(response.getBody()[0].getSiteId()).isNull();
    }

    @Test
    @DisplayName("GET /api/sites/{siteId}/keywords - 사이트별 키워드 조회")
    void getKeywordsBySite_Success() {
        Keyword keyword1 = Keyword.builder()
                .keyword("사이트 키워드 1")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(keyword1);

        Keyword keyword2 = Keyword.builder()
                .keyword("사이트 키워드 2")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(keyword2);

        Keyword globalKeyword = Keyword.builder()
                .keyword("전체 공통 키워드")
                .site(null)
                .active(true)
                .build();
        keywordRepository.save(globalKeyword);

        ResponseEntity<KeywordResponse[]> response = restTemplate.getForEntity(
                "/api/sites/" + testSite.getId() + "/keywords",
                KeywordResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("GET /api/keywords/{keywordId}/alerts - 존재하지 않는 keywordId: 404 반환")
    void getAlertsByKeyword_nonExistentKeywordId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/keywords/99999/alerts", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/keywords?global=false - 지원하지 않는 값: 400 반환")
    void getKeywords_globalFalse_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/keywords?global=false", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/keywords?global=true&active=true - 두 파라미터 동시 사용: 400 반환")
    void getKeywords_globalAndActiveTogether_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/keywords?global=true&active=true", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PATCH /api/keywords/{id} - keyword 빈 문자열: 기존 keyword 유지")
    void patchKeyword_emptyKeyword_doesNotUpdateKeyword() {
        Keyword keyword = Keyword.builder()
                .keyword("원래 키워드")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        KeywordRequest patch = KeywordRequest.builder().keyword("").build();
        ResponseEntity<KeywordResponse> response = restTemplate.exchange(
                "/api/keywords/" + keyword.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                KeywordResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getKeyword()).isEqualTo("원래 키워드");
    }

    @Test
    @DisplayName("키워드 등록 - DB에서 site_id 확인")
    void createKeyword_VerifySiteIdInDatabase() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("DB 검증 키워드")
                .siteId(testSite.getId())
                .active(true)
                .build();

        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSiteId()).isEqualTo(testSite.getId());

        Keyword savedKeyword = keywordRepository.findAll().get(0);
        assertThat(savedKeyword.getSite()).isNotNull();
        assertThat(savedKeyword.getSite().getId()).isEqualTo(testSite.getId());
        assertThat(savedKeyword.getKeyword()).isEqualTo("DB 검증 키워드");
    }
}
