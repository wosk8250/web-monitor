package com.webmonitor.service;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KeywordService 통합 테스트
 */
@SpringBootTest
@Transactional
class KeywordServiceTest {

    @Autowired
    private KeywordService keywordService;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private SiteRepository siteRepository;

    private Site testSite;
    private Keyword testKeyword;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        keywordRepository.deleteAll();
        siteRepository.deleteAll();

        // 테스트용 사이트 생성
        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);

        // 테스트용 키워드 생성
        testKeyword = Keyword.builder()
                .keyword("테스트")
                .site(testSite)
                .active(true)
                .build();
        testKeyword = keywordRepository.save(testKeyword);
    }

    @Test
    @DisplayName("일반 키워드 등록 성공")
    void createKeyword_Success() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("새 키워드")
                .siteId(testSite.getId())
                .active(true)
                .build();

        // When
        Keyword created = keywordService.createKeyword(request);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getKeyword()).isEqualTo("새 키워드");
        assertThat(created.getSite().getId()).isEqualTo(testSite.getId());
        assertThat(keywordRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 공통 키워드 등록 성공 (siteId null)")
    void createKeyword_GlobalKeyword_Success() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("공통 키워드")
                .siteId(null)
                .active(true)
                .build();

        // When
        Keyword created = keywordService.createKeyword(request);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getKeyword()).isEqualTo("공통 키워드");
        assertThat(created.getSite()).isNull();
    }

    @Test
    @DisplayName("공백 키워드 등록 시 새글 감지 활성화")
    void createKeyword_EmptyKeyword_ActivatesContentDetection() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("   ")
                .siteId(testSite.getId())
                .build();

        // When
        Keyword result = keywordService.createKeyword(request);

        // Then
        assertThat(result).isNull(); // 키워드는 저장되지 않음
        Site updatedSite = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(updatedSite.getDetectContentChange()).isTrue();
    }

    @Test
    @DisplayName("공백 키워드를 전체 공통으로 등록 시 예외 발생")
    void createKeyword_EmptyKeywordWithoutSite_ThrowsException() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("")
                .siteId(null)
                .build();

        // When & Then
        assertThatThrownBy(() -> keywordService.createKeyword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공백 키워드(새글 감지)는 특정 사이트에만 설정할 수 있습니다");
    }

    @Test
    @DisplayName("null 키워드 등록 시 예외 발생")
    void createKeyword_NullKeyword_ThrowsException() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword(null)
                .siteId(testSite.getId())
                .build();

        // When & Then
        assertThatThrownBy(() -> keywordService.createKeyword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("키워드는 null일 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 사이트 ID로 키워드 등록 시 예외 발생")
    void createKeyword_NonExistentSite_ThrowsException() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("테스트")
                .siteId(99999L)
                .build();

        // When & Then
        assertThatThrownBy(() -> keywordService.createKeyword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사이트를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("키워드 수정 성공")
    void updateKeyword_Success() {
        // Given
        KeywordRequest request = KeywordRequest.builder()
                .keyword("수정된 키워드")
                .siteId(testSite.getId())
                .active(false)
                .build();

        // When
        Keyword updated = keywordService.updateKeyword(testKeyword.getId(), request);

        // Then
        assertThat(updated.getKeyword()).isEqualTo("수정된 키워드");
        assertThat(updated.getActive()).isFalse();
    }

    @Test
    @DisplayName("키워드 삭제 성공")
    void deleteKeyword_Success() {
        // When
        keywordService.deleteKeyword(testKeyword.getId());

        // Then
        assertThat(keywordRepository.count()).isEqualTo(0);
        assertThat(keywordRepository.findById(testKeyword.getId())).isEmpty();
    }

    @Test
    @DisplayName("ID로 키워드 조회 성공")
    void getKeywordById_Success() {
        // When
        Optional<Keyword> found = keywordService.getKeywordById(testKeyword.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getKeyword()).isEqualTo("테스트");
    }

    @Test
    @DisplayName("모든 키워드 조회")
    void getAllKeywords_ReturnsAll() {
        // Given
        Keyword keyword2 = Keyword.builder()
                .keyword("키워드2")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(keyword2);

        // When
        List<Keyword> keywords = keywordService.getAllKeywords();

        // Then
        assertThat(keywords).hasSize(2);
    }

    @Test
    @DisplayName("활성화된 키워드만 조회")
    void getActiveKeywords_ReturnsOnlyActive() {
        // Given
        Keyword inactiveKeyword = Keyword.builder()
                .keyword("비활성 키워드")
                .site(testSite)
                .active(false)
                .build();
        keywordRepository.save(inactiveKeyword);

        // When
        List<Keyword> activeKeywords = keywordService.getActiveKeywords();

        // Then
        assertThat(activeKeywords).hasSize(1);
        assertThat(activeKeywords.get(0).getActive()).isTrue();
    }

    @Test
    @DisplayName("특정 사이트의 키워드 조회")
    void getKeywordsBySite_ReturnsCorrectKeywords() {
        // Given
        Site anotherSite = Site.builder()
                .name("다른 사이트")
                .url("https://another.com")
                .active(true)
                .build();
        anotherSite = siteRepository.save(anotherSite);

        Keyword keywordForAnotherSite = Keyword.builder()
                .keyword("다른 키워드")
                .site(anotherSite)
                .active(true)
                .build();
        keywordRepository.save(keywordForAnotherSite);

        // When
        List<Keyword> keywordsForTestSite = keywordService.getKeywordsBySite(testSite.getId());
        List<Keyword> keywordsForAnotherSite = keywordService.getKeywordsBySite(anotherSite.getId());

        // Then
        assertThat(keywordsForTestSite).hasSize(1);
        assertThat(keywordsForTestSite.get(0).getSite().getId()).isEqualTo(testSite.getId());
        assertThat(keywordsForAnotherSite).hasSize(1);
        assertThat(keywordsForAnotherSite.get(0).getSite().getId()).isEqualTo(anotherSite.getId());
    }

    @Test
    @DisplayName("전체 공통 키워드 조회")
    void getGlobalKeywords_ReturnsOnlyGlobal() {
        // Given
        Keyword globalKeyword = Keyword.builder()
                .keyword("공통 키워드")
                .site(null)
                .active(true)
                .build();
        keywordRepository.save(globalKeyword);

        // When
        List<Keyword> globalKeywords = keywordService.getGlobalKeywords();

        // Then
        assertThat(globalKeywords).hasSize(1);
        assertThat(globalKeywords.get(0).getSite()).isNull();
        assertThat(globalKeywords.get(0).getKeyword()).isEqualTo("공통 키워드");
    }

    @Test
    @DisplayName("키워드 활성화/비활성화 토글")
    void toggleKeywordActive_Success() {
        // Given
        boolean initialActive = testKeyword.getActive();

        // When
        Keyword toggled = keywordService.toggleKeywordActive(testKeyword.getId());

        // Then
        assertThat(toggled.getActive()).isEqualTo(!initialActive);
    }

    @Test
    @DisplayName("존재하지 않는 키워드 토글 시 예외 발생")
    void toggleKeywordActive_NotFound_ThrowsException() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> keywordService.toggleKeywordActive(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("키워드를 찾을 수 없습니다");
    }
}
