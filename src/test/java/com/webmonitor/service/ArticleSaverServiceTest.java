package com.webmonitor.service;

import com.webmonitor.domain.Article;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.ArticleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleSaverService 단위 테스트")
class ArticleSaverServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleSaverService articleSaverService;

    @Test
    @DisplayName("saveIfNotDuplicate - 정상 저장: true 반환")
    void saveIfNotDuplicate_success_returnsTrue() {
        Article article = buildArticle("https://test.com/article/1");
        when(articleRepository.save(any())).thenReturn(article);

        boolean result = articleSaverService.saveIfNotDuplicate(article);

        assertThat(result).isTrue();
        verify(articleRepository, times(1)).save(article);
    }

    @Test
    @DisplayName("saveIfNotDuplicate - unique constraint 위반: false 반환, 예외 전파 없음")
    void saveIfNotDuplicate_duplicateKey_returnsFalse() {
        Article article = buildArticle("https://test.com/article/1");
        doThrow(new DataIntegrityViolationException("Unique constraint violation"))
                .when(articleRepository).save(any());

        boolean result = articleSaverService.saveIfNotDuplicate(article);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("saveIfNotDuplicate - 다른 RuntimeException: 그대로 전파")
    void saveIfNotDuplicate_otherException_propagates() {
        Article article = buildArticle("https://test.com/article/1");
        doThrow(new RuntimeException("DB 연결 실패"))
                .when(articleRepository).save(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> articleSaverService.saveIfNotDuplicate(article))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 연결 실패");
    }

    private Article buildArticle(String url) {
        Site site = Site.builder()
                .name("테스트 사이트").url("https://test.com").active(true).build();
        ReflectionTestUtils.setField(site, "id", 1L);
        return Article.builder()
                .site(site)
                .articleUrl(url)
                .articleTitle("테스트 제목")
                .articleId("1")
                .build();
    }
}
