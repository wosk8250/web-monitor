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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KeywordController 통합 테스트
 */
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
        // 테스트 데이터 초기화
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
    @DisplayName("키워드 등록 - 정상 케이스 (siteId 지정)")
    void createKeyword_WithSiteId_Success() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("테스트 키워드")
                .siteId(testSite.getId())
                .active(true)
                .build();

        // When
        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getKeyword()).isEqualTo("테스트 키워드");
        assertThat(response.getBody().getSiteId()).isEqualTo(testSite.getId());
        assertThat(response.getBody().getSiteName()).isEqualTo("테스트 사이트");
        assertThat(response.getBody().getActive()).isTrue();

        // DB 확인
        assertThat(keywordRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드 등록 - 정상 케이스 (전체 공통 키워드, siteId null)")
    void createKeyword_WithoutSiteId_Success() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("공통 키워드")
                .siteId(null)  // 전체 공통 키워드
                .active(true)
                .build();

        // When
        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getKeyword()).isEqualTo("공통 키워드");
        assertThat(response.getBody().getSiteId()).isNull();
        assertThat(response.getBody().getSiteName()).isEqualTo("전체 공통");
        assertThat(response.getBody().getActive()).isTrue();

        // DB 확인
        Keyword savedKeyword = keywordRepository.findAll().get(0);
        assertThat(savedKeyword.getSite()).isNull();
    }

    @Test
    @DisplayName("공백 키워드 등록 → 새글 감지 자동 활성화")
    void createKeyword_WithEmptyKeyword_EnablesContentDetection() {
        // Given: detectContentChange=false인 사이트
        testSite.setDetectContentChange(false);
        testSite = siteRepository.save(testSite);

        KeywordRequest request = KeywordRequest.builder()
                .keyword("")  // 빈 문자열 → 새글 감지 활성화
                .siteId(testSite.getId())
                .active(true)
                .build();

        // When
        ResponseEntity<java.util.Map> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                java.util.Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("message")).isEqualTo("새글 감지 기능이 활성화되었습니다");

        // DB 확인: Site의 detectContentChange가 true로 변경됨
        Site updatedSite = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(updatedSite.getDetectContentChange()).isTrue();

        // 키워드는 저장되지 않음
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("공백 키워드 등록 - 실패 (siteId null)")
    void createKeyword_WithEmptyKeywordAndNullSiteId_Fail() {
        // Given: 공백 키워드는 특정 사이트에만 허용
        KeywordRequest request = KeywordRequest.builder()
                .keyword("")  // 빈 문자열
                .siteId(null)  // 전체 공통 키워드로는 불가
                .active(true)
                .build();

        // When
        ResponseEntity<java.util.Map> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                java.util.Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("키워드 등록 - 실패 (keyword가 null)")
    void createKeyword_WithNullKeyword_Fail() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword(null)  // null
                .siteId(testSite.getId())
                .active(true)
                .build();

        // When
        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("키워드 등록 - 실패 (존재하지 않는 siteId)")
    void createKeyword_WithNonExistentSiteId_Fail() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("테스트 키워드")
                .siteId(99999L)  // 존재하지 않는 siteId
                .active(true)
                .build();

        // When
        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(keywordRepository.count()).isZero();
    }

    @Test
    @DisplayName("키워드 수정 - 정상 케이스")
    void updateKeyword_Success() {
        // Given
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

        // When
        restTemplate.put("/api/keywords/" + keyword.getId(), updateRequest);

        // Then
        Keyword updatedKeyword = keywordRepository.findById(keyword.getId()).orElseThrow();
        assertThat(updatedKeyword.getKeyword()).isEqualTo("수정된 키워드");
        assertThat(updatedKeyword.getActive()).isFalse();
    }

    @Test
    @DisplayName("키워드 삭제 - 정상 케이스")
    void deleteKeyword_Success() {
        // Given
        Keyword keyword = Keyword.builder()
                .keyword("삭제될 키워드")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        // When
        restTemplate.delete("/api/keywords/" + keyword.getId());

        // Then
        assertThat(keywordRepository.existsById(keyword.getId())).isFalse();
    }

    @Test
    @DisplayName("키워드 활성화 토글 - 정상 케이스")
    void toggleKeywordActive_Success() {
        // Given
        Keyword keyword = Keyword.builder()
                .keyword("토글 테스트")
                .site(testSite)
                .active(true)
                .build();
        keyword = keywordRepository.save(keyword);

        // When
        ResponseEntity<KeywordResponse> response = restTemplate.exchange(
                "/api/keywords/" + keyword.getId() + "/toggle",
                org.springframework.http.HttpMethod.PATCH,
                null,
                KeywordResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getActive()).isFalse();

        // DB 확인
        Keyword toggledKeyword = keywordRepository.findById(keyword.getId()).orElseThrow();
        assertThat(toggledKeyword.getActive()).isFalse();
    }

    @Test
    @DisplayName("전체 공통 키워드 조회")
    void getGlobalKeywords_Success() {
        // Given
        Keyword globalKeyword = Keyword.builder()
                .keyword("전체 공통 키워드")
                .site(null)  // 전체 공통
                .active(true)
                .build();
        keywordRepository.save(globalKeyword);

        Keyword siteKeyword = Keyword.builder()
                .keyword("사이트 키워드")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(siteKeyword);

        // When
        ResponseEntity<KeywordResponse[]> response = restTemplate.getForEntity(
                "/api/keywords/global",
                KeywordResponse[].class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getKeyword()).isEqualTo("전체 공통 키워드");
        assertThat(response.getBody()[0].getSiteId()).isNull();
    }

    @Test
    @DisplayName("사이트별 키워드 조회")
    void getKeywordsBySite_Success() {
        // Given
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

        // When
        ResponseEntity<KeywordResponse[]> response = restTemplate.getForEntity(
                "/api/keywords/site/" + testSite.getId(),
                KeywordResponse[].class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("키워드 등록 - DB에서 site_id 확인")
    void createKeyword_VerifySiteIdInDatabase() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("DB 검증 키워드")
                .siteId(testSite.getId())
                .active(true)
                .build();

        // When
        ResponseEntity<KeywordResponse> response = restTemplate.postForEntity(
                "/api/keywords",
                request,
                KeywordResponse.class
        );

        // Then - API 응답 확인
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSiteId()).isEqualTo(testSite.getId());

        // Then - DB에서 직접 확인
        Keyword savedKeyword = keywordRepository.findAll().get(0);
        assertThat(savedKeyword.getSite()).isNotNull();
        assertThat(savedKeyword.getSite().getId()).isEqualTo(testSite.getId());
        assertThat(savedKeyword.getKeyword()).isEqualTo("DB 검증 키워드");
    }
}
