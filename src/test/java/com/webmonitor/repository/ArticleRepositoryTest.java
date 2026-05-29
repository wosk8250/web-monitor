package com.webmonitor.repository;

import com.webmonitor.domain.Article;
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
 * ArticleRepository 통합 테스트
 * H2 in-memory 데이터베이스를 사용한 Repository 계층 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class ArticleRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SiteRepository siteRepository;

    private Site testSite1;
    private Site testSite2;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
        siteRepository.deleteAll();

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
    void save_shouldPersistArticle() {
        // Given
        Article article = createTestArticle(testSite1, "12345", "Test Article", "https://test.com/article/12345");

        // When
        Article saved = articleRepository.save(article);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getArticleId()).isEqualTo("12345");
        assertThat(saved.getArticleTitle()).isEqualTo("Test Article");
        assertThat(saved.getArticleUrl()).isEqualTo("https://test.com/article/12345");
        assertThat(saved.getSite()).isEqualTo(testSite1);
        assertThat(saved.getFirstDetectedAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnArticle() {
        // Given
        Article article = createTestArticle(testSite1, "find-test", "Find Test", "https://test.com/find");
        Article saved = articleRepository.save(article);

        // When
        Optional<Article> found = articleRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getArticleTitle()).isEqualTo("Find Test");
    }

    @Test
    void findBySiteAndArticleUrl_shouldReturnArticle() {
        // Given
        String articleUrl = "https://test1.com/article/123";
        Article article = createTestArticle(testSite1, "123", "Article 123", articleUrl);
        articleRepository.save(article);

        // When
        Optional<Article> found = articleRepository.findBySiteAndArticleUrl(testSite1, articleUrl);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getArticleUrl()).isEqualTo(articleUrl);
        assertThat(found.get().getSite()).isEqualTo(testSite1);
    }

    @Test
    void findBySiteAndArticleId_shouldReturnArticle() {
        // Given
        String articleId = "article-456";
        Article article = createTestArticle(testSite1, articleId, "Article 456", "https://test1.com/456");
        articleRepository.save(article);

        // When
        Optional<Article> found = articleRepository.findBySiteAndArticleId(testSite1, articleId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getArticleId()).isEqualTo(articleId);
        assertThat(found.get().getSite()).isEqualTo(testSite1);
    }

    @Test
    void findBySite_shouldReturnAllArticlesForSite() {
        // Given
        Article article1 = createTestArticle(testSite1, "1", "Article 1", "https://test1.com/1");
        Article article2 = createTestArticle(testSite1, "2", "Article 2", "https://test1.com/2");
        Article article3 = createTestArticle(testSite2, "3", "Article 3", "https://test2.com/3");
        articleRepository.saveAll(List.of(article1, article2, article3));

        // When
        List<Article> site1Articles = articleRepository.findBySite(testSite1);
        List<Article> site2Articles = articleRepository.findBySite(testSite2);

        // Then
        assertThat(site1Articles).hasSize(2);
        assertThat(site1Articles).extracting(Article::getArticleTitle)
                .containsExactlyInAnyOrder("Article 1", "Article 2");
        assertThat(site2Articles).hasSize(1);
        assertThat(site2Articles.get(0).getArticleTitle()).isEqualTo("Article 3");
    }

    @Test
    void existsBySiteAndArticleUrl_shouldCheckExistence() {
        // Given
        String existingUrl = "https://test1.com/exists";
        String nonExistingUrl = "https://test1.com/not-exists";
        Article article = createTestArticle(testSite1, "exists", "Exists", existingUrl);
        articleRepository.save(article);

        // When
        boolean exists = articleRepository.existsBySiteAndArticleUrl(testSite1, existingUrl);
        boolean notExists = articleRepository.existsBySiteAndArticleUrl(testSite1, nonExistingUrl);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void existsBySiteAndArticleId_shouldCheckExistence() {
        // Given
        String existingId = "exists-id";
        String nonExistingId = "not-exists-id";
        Article article = createTestArticle(testSite1, existingId, "Exists", "https://test1.com/exists");
        articleRepository.save(article);

        // When
        boolean exists = articleRepository.existsBySiteAndArticleId(testSite1, existingId);
        boolean notExists = articleRepository.existsBySiteAndArticleId(testSite1, nonExistingId);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void deleteBySite_shouldRemoveAllArticlesForSite() {
        // Given
        Article article1 = createTestArticle(testSite1, "1", "Article 1", "https://test1.com/1");
        Article article2 = createTestArticle(testSite1, "2", "Article 2", "https://test1.com/2");
        Article article3 = createTestArticle(testSite2, "3", "Article 3", "https://test2.com/3");
        articleRepository.saveAll(List.of(article1, article2, article3));

        // When
        articleRepository.deleteBySite(testSite1);
        articleRepository.flush();

        // Then
        List<Article> remainingSite1Articles = articleRepository.findBySite(testSite1);
        List<Article> remainingSite2Articles = articleRepository.findBySite(testSite2);

        assertThat(remainingSite1Articles).isEmpty();
        assertThat(remainingSite2Articles).hasSize(1);
        assertThat(remainingSite2Articles.get(0).getArticleTitle()).isEqualTo("Article 3");
    }

    @Test
    void delete_shouldRemoveArticle() {
        // Given
        Article article = createTestArticle(testSite1, "delete", "To Delete", "https://test1.com/delete");
        Article saved = articleRepository.save(article);

        // When
        articleRepository.deleteById(saved.getId());

        // Then
        assertThat(articleRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void update_shouldModifyArticle() {
        // Given
        Article article = createTestArticle(testSite1, "update", "Original Title", "https://test1.com/update");
        Article saved = articleRepository.save(article);

        // When
        saved.setArticleTitle("Updated Title");
        Article updated = articleRepository.save(saved);

        // Then
        assertThat(updated.getArticleTitle()).isEqualTo("Updated Title");
    }

    @Test
    void save_withNullArticleId_shouldPersist() {
        // Given
        Article article = Article.builder()
                .site(testSite1)
                .articleId(null)  // 게시글 ID가 없는 경우
                .articleTitle("No ID Article")
                .articleUrl("https://test1.com/no-id")
                .build();

        // When
        Article saved = articleRepository.save(article);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getArticleId()).isNull();
        assertThat(saved.getArticleTitle()).isEqualTo("No ID Article");
    }

    @Test
    void count_shouldReturnCorrectCount() {
        // Given
        Article article1 = createTestArticle(testSite1, "1", "Article 1", "https://test1.com/1");
        Article article2 = createTestArticle(testSite2, "2", "Article 2", "https://test2.com/2");
        articleRepository.saveAll(List.of(article1, article2));

        // When
        long count = articleRepository.count();

        // Then
        assertThat(count).isEqualTo(2);
    }

    /**
     * 테스트용 Article 생성 헬퍼 메서드
     */
    private Article createTestArticle(Site site, String articleId, String title, String url) {
        return Article.builder()
                .site(site)
                .articleId(articleId)
                .articleTitle(title)
                .articleUrl(url)
                .build();
    }
}
