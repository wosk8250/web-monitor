package com.webmonitor.repository;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KeywordRepository 통합 테스트
 * H2 in-memory 데이터베이스를 사용한 Repository 계층 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class KeywordRepositoryTest {

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private SiteRepository siteRepository;

    private Site testSite1;
    private Site testSite2;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        keywordRepository.deleteAll();
        siteRepository.deleteAll();

        // 테스트 사이트 생성
        testSite1 = Site.builder()
                .name("Test Site 1")
                .url("https://test1.com")
                .active(true)
                .checkIntervalMinutes(5)
                .build();
        siteRepository.save(testSite1);

        testSite2 = Site.builder()
                .name("Test Site 2")
                .url("https://test2.com")
                .active(true)
                .checkIntervalMinutes(5)
                .build();
        siteRepository.save(testSite2);
    }

    @Test
    void save_shouldPersistKeyword() {
        // Given
        Keyword keyword = createTestKeyword("test keyword", testSite1, true);

        // When
        Keyword savedKeyword = keywordRepository.save(keyword);

        // Then
        assertThat(savedKeyword.getId()).isNotNull();
        assertThat(savedKeyword.getKeyword()).isEqualTo("test keyword");
        assertThat(savedKeyword.getSite()).isEqualTo(testSite1);
        assertThat(savedKeyword.getActive()).isTrue();
        assertThat(savedKeyword.getCreatedAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnKeyword() {
        // Given
        Keyword keyword = createTestKeyword("find test", testSite1, true);
        Keyword saved = keywordRepository.save(keyword);

        // When
        Optional<Keyword> found = keywordRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getKeyword()).isEqualTo("find test");
    }

    @Test
    void findBySite_shouldReturnKeywordsForSite() {
        // Given
        Keyword keyword1 = createTestKeyword("keyword1", testSite1, true);
        Keyword keyword2 = createTestKeyword("keyword2", testSite1, true);
        Keyword keyword3 = createTestKeyword("keyword3", testSite2, true);
        keywordRepository.saveAll(List.of(keyword1, keyword2, keyword3));

        // When
        List<Keyword> site1Keywords = keywordRepository.findBySite(testSite1);
        List<Keyword> site2Keywords = keywordRepository.findBySite(testSite2);

        // Then
        assertThat(site1Keywords).hasSize(2);
        assertThat(site1Keywords).extracting(Keyword::getKeyword)
                .containsExactlyInAnyOrder("keyword1", "keyword2");
        assertThat(site2Keywords).hasSize(1);
        assertThat(site2Keywords.get(0).getKeyword()).isEqualTo("keyword3");
    }

    @Test
    void findBySiteAndActive_shouldReturnActiveKeywords() {
        // Given
        Keyword activeKeyword = createTestKeyword("active", testSite1, true);
        Keyword inactiveKeyword = createTestKeyword("inactive", testSite1, false);
        keywordRepository.saveAll(List.of(activeKeyword, inactiveKeyword));

        // When
        List<Keyword> activeKeywords = keywordRepository.findBySiteAndActive(testSite1, true);
        List<Keyword> inactiveKeywords = keywordRepository.findBySiteAndActive(testSite1, false);

        // Then
        assertThat(activeKeywords).hasSize(1);
        assertThat(activeKeywords.get(0).getKeyword()).isEqualTo("active");
        assertThat(inactiveKeywords).hasSize(1);
        assertThat(inactiveKeywords.get(0).getKeyword()).isEqualTo("inactive");
    }

    @Test
    void findByActive_shouldReturnAllActiveKeywords() {
        // Given
        Keyword active1 = createTestKeyword("active1", testSite1, true);
        Keyword active2 = createTestKeyword("active2", testSite2, true);
        Keyword inactive1 = createTestKeyword("inactive1", testSite1, false);
        Keyword inactive2 = createTestKeyword("inactive2", testSite2, false);
        keywordRepository.saveAll(List.of(active1, active2, inactive1, inactive2));

        // When
        List<Keyword> activeKeywords = keywordRepository.findByActive(true);
        List<Keyword> inactiveKeywords = keywordRepository.findByActive(false);

        // Then
        assertThat(activeKeywords).hasSize(2);
        assertThat(activeKeywords).extracting(Keyword::getKeyword)
                .containsExactlyInAnyOrder("active1", "active2");
        assertThat(inactiveKeywords).hasSize(2);
        assertThat(inactiveKeywords).extracting(Keyword::getKeyword)
                .containsExactlyInAnyOrder("inactive1", "inactive2");
    }

    @Test
    void findByKeywordContaining_shouldReturnMatchingKeywords() {
        // Given
        Keyword keyword1 = createTestKeyword("Java programming", testSite1, true);
        Keyword keyword2 = createTestKeyword("JavaScript tutorial", testSite1, true);
        Keyword keyword3 = createTestKeyword("Python guide", testSite2, true);
        keywordRepository.saveAll(List.of(keyword1, keyword2, keyword3));

        // When
        List<Keyword> javaKeywords = keywordRepository.findByKeywordContaining("Java");
        List<Keyword> pythonKeywords = keywordRepository.findByKeywordContaining("Python");

        // Then
        assertThat(javaKeywords).hasSize(2);
        assertThat(javaKeywords).extracting(Keyword::getKeyword)
                .containsExactlyInAnyOrder("Java programming", "JavaScript tutorial");
        assertThat(pythonKeywords).hasSize(1);
        assertThat(pythonKeywords.get(0).getKeyword()).isEqualTo("Python guide");
    }

    @Test
    void findBySiteId_shouldReturnKeywordsBySiteId() {
        // Given
        Keyword keyword1 = createTestKeyword("keyword1", testSite1, true);
        Keyword keyword2 = createTestKeyword("keyword2", testSite1, true);
        Keyword keyword3 = createTestKeyword("keyword3", testSite2, true);
        keywordRepository.saveAll(List.of(keyword1, keyword2, keyword3));

        // When
        List<Keyword> site1Keywords = keywordRepository.findBySiteId(testSite1.getId());
        List<Keyword> site2Keywords = keywordRepository.findBySiteId(testSite2.getId());

        // Then
        assertThat(site1Keywords).hasSize(2);
        assertThat(site1Keywords).extracting(Keyword::getKeyword)
                .containsExactlyInAnyOrder("keyword1", "keyword2");
        assertThat(site2Keywords).hasSize(1);
        assertThat(site2Keywords.get(0).getKeyword()).isEqualTo("keyword3");
    }

    @Test
    void findBySiteIsNull_shouldReturnGlobalKeywords() {
        // Given - 전체 공통 키워드 (site = null)
        Keyword globalKeyword1 = createGlobalKeyword("global1", true);
        Keyword globalKeyword2 = createGlobalKeyword("global2", true);
        Keyword siteKeyword = createTestKeyword("site keyword", testSite1, true);
        keywordRepository.saveAll(List.of(globalKeyword1, globalKeyword2, siteKeyword));

        // When
        List<Keyword> globalKeywords = keywordRepository.findBySiteIsNull();

        // Then
        assertThat(globalKeywords).hasSize(2);
        assertThat(globalKeywords).extracting(Keyword::getKeyword)
                .containsExactlyInAnyOrder("global1", "global2");
        assertThat(globalKeywords).allMatch(k -> k.getSite() == null);
    }

    @Test
    void deleteBySiteId_shouldRemoveAllKeywordsForSite() {
        // Given
        Keyword keyword1 = createTestKeyword("keyword1", testSite1, true);
        Keyword keyword2 = createTestKeyword("keyword2", testSite1, true);
        Keyword keyword3 = createTestKeyword("keyword3", testSite2, true);
        keywordRepository.saveAll(List.of(keyword1, keyword2, keyword3));

        // When
        keywordRepository.deleteBySiteId(testSite1.getId());
        keywordRepository.flush();

        // Then
        List<Keyword> remainingSite1Keywords = keywordRepository.findBySite(testSite1);
        List<Keyword> remainingSite2Keywords = keywordRepository.findBySite(testSite2);

        assertThat(remainingSite1Keywords).isEmpty();
        assertThat(remainingSite2Keywords).hasSize(1);
        assertThat(remainingSite2Keywords.get(0).getKeyword()).isEqualTo("keyword3");
    }

    @Test
    void delete_shouldRemoveKeyword() {
        // Given
        Keyword keyword = createTestKeyword("to delete", testSite1, true);
        Keyword saved = keywordRepository.save(keyword);

        // When
        keywordRepository.deleteById(saved.getId());

        // Then
        assertThat(keywordRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void update_shouldModifyKeyword() {
        // Given
        Keyword keyword = createTestKeyword("original", testSite1, true);
        Keyword saved = keywordRepository.save(keyword);

        // When
        saved.setKeyword("updated");
        saved.setActive(false);
        Keyword updated = keywordRepository.save(saved);

        // Then
        assertThat(updated.getKeyword()).isEqualTo("updated");
        assertThat(updated.getActive()).isFalse();
    }

    @Test
    void save_withActiveTrue_shouldPersist() {
        // Given
        Keyword keyword = Keyword.builder()
                .keyword("active keyword")
                .site(testSite1)
                .active(true)
                .build();

        // When
        Keyword saved = keywordRepository.save(keyword);

        // Then
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    void save_globalKeyword_shouldAllowNullSite() {
        // Given
        Keyword globalKeyword = Keyword.builder()
                .keyword("global keyword")
                .site(null)  // 전체 공통 키워드
                .active(true)
                .build();

        // When
        Keyword saved = keywordRepository.save(globalKeyword);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSite()).isNull();
        assertThat(saved.getKeyword()).isEqualTo("global keyword");
    }

    @Test
    void findAll_shouldReturnAllKeywords() {
        // Given
        Keyword keyword1 = createTestKeyword("keyword1", testSite1, true);
        Keyword keyword2 = createTestKeyword("keyword2", testSite2, true);
        Keyword globalKeyword = createGlobalKeyword("global", true);
        keywordRepository.saveAll(List.of(keyword1, keyword2, globalKeyword));

        // When
        List<Keyword> allKeywords = keywordRepository.findAll();

        // Then
        assertThat(allKeywords).hasSize(3);
    }

    @Test
    void count_shouldReturnCorrectCount() {
        // Given
        Keyword keyword1 = createTestKeyword("keyword1", testSite1, true);
        Keyword keyword2 = createTestKeyword("keyword2", testSite2, true);
        keywordRepository.saveAll(List.of(keyword1, keyword2));

        // When
        long count = keywordRepository.count();

        // Then
        assertThat(count).isEqualTo(2);
    }

    /**
     * 테스트용 Keyword 생성 헬퍼 메서드
     */
    private Keyword createTestKeyword(String keyword, Site site, Boolean active) {
        return Keyword.builder()
                .keyword(keyword)
                .site(site)
                .active(active)
                .build();
    }

    /**
     * 전체 공통 키워드 생성 헬퍼 메서드 (site = null)
     */
    private Keyword createGlobalKeyword(String keyword, Boolean active) {
        return Keyword.builder()
                .keyword(keyword)
                .site(null)  // 전체 공통 키워드
                .active(active)
                .build();
    }
}
