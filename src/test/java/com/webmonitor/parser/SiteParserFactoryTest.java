package com.webmonitor.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SiteParserFactory 테스트
 */
class SiteParserFactoryTest {

    private SiteParserFactory factory;
    private HackerNewsParser hackerNewsParser;
    private ClienParser clienParser;

    @BeforeEach
    void setUp() {
        hackerNewsParser = new HackerNewsParser();
        clienParser = new ClienParser();
        factory = new SiteParserFactory(List.of(hackerNewsParser, clienParser));
    }

    @Test
    @DisplayName("getParser - Hacker News URL에 대해 HackerNewsParser 반환")
    void getParser_HackerNewsUrl_ReturnsHackerNewsParser() {
        // Given
        String url = "https://news.ycombinator.com/";

        // When
        SiteParser parser = factory.getParser(url);

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser).isInstanceOf(HackerNewsParser.class);
    }

    @Test
    @DisplayName("getParser - 클리앙 URL에 대해 ClienParser 반환")
    void getParser_ClienUrl_ReturnsClienParser() {
        // Given
        String url = "https://www.clien.net/service/board/park";

        // When
        SiteParser parser = factory.getParser(url);

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser).isInstanceOf(ClienParser.class);
    }

    @Test
    @DisplayName("getParser - 서브도메인 포함 클리앙 URL도 매칭")
    void getParser_ClienWithSubdomain_ReturnsClienParser() {
        // Given
        String url = "https://www.clien.net/service/board/test";

        // When
        SiteParser parser = factory.getParser(url);

        // Then
        assertThat(parser).isNotNull();
        assertThat(parser).isInstanceOf(ClienParser.class);
    }

    @Test
    @DisplayName("getParser - 지원하지 않는 도메인은 null 반환")
    void getParser_UnsupportedDomain_ReturnsNull() {
        // Given
        String url = "https://example.com/test";

        // When
        SiteParser parser = factory.getParser(url);

        // Then
        assertThat(parser).isNull();
    }

    @Test
    @DisplayName("getParser - 잘못된 URL 형식은 null 반환")
    void getParser_InvalidUrl_ReturnsNull() {
        // Given
        String url = "not-a-valid-url";

        // When
        SiteParser parser = factory.getParser(url);

        // Then
        assertThat(parser).isNull();
    }

    @Test
    @DisplayName("getParser - host가 null인 URL은 null 반환")
    void getParser_UrlWithoutHost_ReturnsNull() {
        // Given
        String url = "file:///test/path";

        // When
        SiteParser parser = factory.getParser(url);

        // Then
        assertThat(parser).isNull();
    }

    @Test
    @DisplayName("hasParser - 지원하는 도메인은 true 반환")
    void hasParser_SupportedDomain_ReturnsTrue() {
        // Given
        String url = "https://news.ycombinator.com/";

        // When
        boolean hasParser = factory.hasParser(url);

        // Then
        assertThat(hasParser).isTrue();
    }

    @Test
    @DisplayName("hasParser - 지원하지 않는 도메인은 false 반환")
    void hasParser_UnsupportedDomain_ReturnsFalse() {
        // Given
        String url = "https://example.com/";

        // When
        boolean hasParser = factory.hasParser(url);

        // Then
        assertThat(hasParser).isFalse();
    }

    @Test
    @DisplayName("hasParser - 잘못된 URL은 false 반환")
    void hasParser_InvalidUrl_ReturnsFalse() {
        // Given
        String url = "invalid-url";

        // When
        boolean hasParser = factory.hasParser(url);

        // Then
        assertThat(hasParser).isFalse();
    }

    @Test
    @DisplayName("getParser - 파서 목록이 비어있으면 null 반환")
    void getParser_EmptyParserList_ReturnsNull() {
        // Given
        SiteParserFactory emptyFactory = new SiteParserFactory(List.of());
        String url = "https://news.ycombinator.com/";

        // When
        SiteParser parser = emptyFactory.getParser(url);

        // Then
        assertThat(parser).isNull();
    }
}
