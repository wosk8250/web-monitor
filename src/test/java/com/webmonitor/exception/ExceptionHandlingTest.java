package com.webmonitor.exception;

import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.ArticleRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GlobalExceptionHandler 테스트
 * API 엔드포인트의 예외 처리 동작 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExceptionHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        articleRepository.deleteAll();
        keywordRepository.deleteAll();
        siteRepository.deleteAll();
    }

    @Test
    @DisplayName("존재하지 않는 사이트 조회 - 404 응답")
    void testSiteNotFound() throws Exception {
        // Given: 존재하지 않는 사이트 ID

        // When & Then: 404 Not Found 응답 검증
        mockMvc.perform(get("/api/sites/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("존재하지 않는 사이트 토글 - 404 응답")
    void testToggleNonExistentSite() throws Exception {
        // Given: 존재하지 않는 사이트 ID

        // When & Then: Controller는 IllegalArgumentException을 잡아서 404 반환
        mockMvc.perform(patch("/api/sites/999/toggle"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("존재하지 않는 사이트 삭제 - 404 응답")
    void testDeleteNonExistentSite() throws Exception {
        // Given: 존재하지 않는 사이트 ID

        // When & Then: Controller는 IllegalArgumentException을 잡아서 404 반환
        mockMvc.perform(delete("/api/sites/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API 정상 동작 확인 - 전체 사이트 조회")
    void testGetAllSites() throws Exception {
        // Given: 빈 데이터베이스

        // When & Then: 200 OK 응답
        mockMvc.perform(get("/api/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
