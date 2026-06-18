package com.webmonitor.service;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.dto.KeywordResponse;
import com.webmonitor.exception.resource.KeywordNotFoundException;
import com.webmonitor.exception.resource.SiteNotFoundException;
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
        keywordRepository.deleteAll();
        siteRepository.deleteAll();

        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);

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
        KeywordRequest request = KeywordRequest.builder()
                .keyword("새 키워드")
                .siteId(testSite.getId())
                .active(true)
                .build();

        Optional<KeywordResponse> result = keywordService.createKeyword(request);

        assertThat(result).isPresent();
        assertThat(result.get().getKeyword()).isEqualTo("새 키워드");
        assertThat(result.get().getSiteId()).isEqualTo(testSite.getId());
        assertThat(keywordRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 공통 키워드 등록 성공 (siteId null)")
    void createKeyword_GlobalKeyword_Success() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("공통 키워드")
                .siteId(null)
                .active(true)
                .build();

        Optional<KeywordResponse> result = keywordService.createKeyword(request);

        assertThat(result).isPresent();
        assertThat(result.get().getKeyword()).isEqualTo("공통 키워드");
        assertThat(result.get().getSiteId()).isNull();
    }

    @Test
    @DisplayName("공백 키워드 등록 시 새글 감지 활성화, Optional.empty() 반환")
    void createKeyword_EmptyKeyword_ActivatesContentDetection() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("   ")
                .siteId(testSite.getId())
                .build();

        Optional<KeywordResponse> result = keywordService.createKeyword(request);

        assertThat(result).isEmpty();
        Site updatedSite = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(updatedSite.getDetectContentChange()).isTrue();
    }

    @Test
    @DisplayName("공백 키워드를 전체 공통으로 등록 시 예외 발생")
    void createKeyword_EmptyKeywordWithoutSite_ThrowsException() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("")
                .siteId(null)
                .build();

        assertThatThrownBy(() -> keywordService.createKeyword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공백 키워드(새글 감지)는 특정 사이트에만 설정할 수 있습니다");
    }

    @Test
    @DisplayName("null 키워드 등록 시 예외 발생")
    void createKeyword_NullKeyword_ThrowsException() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword(null)
                .siteId(testSite.getId())
                .build();

        assertThatThrownBy(() -> keywordService.createKeyword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("키워드는 null일 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 사이트 ID로 키워드 등록 시 예외 발생")
    void createKeyword_NonExistentSite_ThrowsException() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("테스트")
                .siteId(99999L)
                .build();

        assertThatThrownBy(() -> keywordService.createKeyword(request))
                .isInstanceOf(SiteNotFoundException.class);
    }

    @Test
    @DisplayName("키워드 수정 성공")
    void updateKeyword_Success() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("수정된 키워드")
                .siteId(testSite.getId())
                .active(false)
                .build();

        KeywordResponse updated = keywordService.updateKeyword(testKeyword.getId(), request);

        assertThat(updated.getKeyword()).isEqualTo("수정된 키워드");
        assertThat(updated.getActive()).isFalse();
    }

    @Test
    @DisplayName("키워드 삭제 성공")
    void deleteKeyword_Success() {
        keywordService.deleteKeyword(testKeyword.getId());

        assertThat(keywordRepository.count()).isEqualTo(0);
        assertThat(keywordRepository.findById(testKeyword.getId())).isEmpty();
    }

    @Test
    @DisplayName("ID로 키워드 조회 성공")
    void getKeywordById_Success() {
        Optional<KeywordResponse> found = keywordService.getKeywordById(testKeyword.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getKeyword()).isEqualTo("테스트");
    }

    @Test
    @DisplayName("모든 키워드 조회")
    void getAllKeywords_ReturnsAll() {
        Keyword keyword2 = Keyword.builder()
                .keyword("키워드2")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(keyword2);

        List<KeywordResponse> keywords = keywordService.getAllKeywords();

        assertThat(keywords).hasSize(2);
    }

    @Test
    @DisplayName("활성화된 키워드만 조회")
    void getActiveKeywords_ReturnsOnlyActive() {
        Keyword inactiveKeyword = Keyword.builder()
                .keyword("비활성 키워드")
                .site(testSite)
                .active(false)
                .build();
        keywordRepository.save(inactiveKeyword);

        List<KeywordResponse> activeKeywords = keywordService.getActiveKeywords();

        assertThat(activeKeywords).hasSize(1);
        assertThat(activeKeywords.get(0).getActive()).isTrue();
    }

    @Test
    @DisplayName("특정 사이트의 키워드 조회")
    void getKeywordsBySite_ReturnsCorrectKeywords() {
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

        List<KeywordResponse> keywordsForTestSite = keywordService.getKeywordsBySite(testSite.getId());
        List<KeywordResponse> keywordsForAnotherSite = keywordService.getKeywordsBySite(anotherSite.getId());

        assertThat(keywordsForTestSite).hasSize(1);
        assertThat(keywordsForTestSite.get(0).getSiteId()).isEqualTo(testSite.getId());
        assertThat(keywordsForAnotherSite).hasSize(1);
        assertThat(keywordsForAnotherSite.get(0).getSiteId()).isEqualTo(anotherSite.getId());
    }

    @Test
    @DisplayName("전체 공통 키워드 조회")
    void getGlobalKeywords_ReturnsOnlyGlobal() {
        Keyword globalKeyword = Keyword.builder()
                .keyword("공통 키워드")
                .site(null)
                .active(true)
                .build();
        keywordRepository.save(globalKeyword);

        List<KeywordResponse> globalKeywords = keywordService.getGlobalKeywords();

        assertThat(globalKeywords).hasSize(1);
        assertThat(globalKeywords.get(0).getSiteId()).isNull();
        assertThat(globalKeywords.get(0).getKeyword()).isEqualTo("공통 키워드");
    }

    @Test
    @DisplayName("키워드 부분 수정 - active만 변경 시 keyword와 siteId 유지")
    void updateKeyword_partialUpdate_onlyActive_preservesKeywordAndSite() {
        KeywordRequest request = KeywordRequest.builder()
                .active(false)
                .build();

        KeywordResponse updated = keywordService.updateKeyword(testKeyword.getId(), request);

        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getKeyword()).isEqualTo("테스트");
        assertThat(updated.getSiteId()).isEqualTo(testSite.getId());
    }

    @Test
    @DisplayName("존재하지 않는 키워드 수정 시 예외 발생")
    void updateKeyword_notFound_throwsException() {
        KeywordRequest request = KeywordRequest.builder()
                .keyword("수정 키워드")
                .siteId(testSite.getId())
                .build();

        assertThatThrownBy(() -> keywordService.updateKeyword(99999L, request))
                .isInstanceOf(KeywordNotFoundException.class);
    }

    // ===============================
    // addKeywordToSite 테스트
    // ===============================

    @Test
    @DisplayName("addKeywordToSite: detectContentChange=true 사이트에 키워드 추가 시 detectContentChange=false 로 전환")
    void addKeywordToSite_WhenDetectContentChangeIsTrue_ShouldSwitchToKeywordMode() {
        Site site = siteRepository.save(Site.builder()
                .name("콘텐츠 감지 사이트")
                .url("https://content.com")
                .active(true)
                .detectContentChange(true)
                .build());

        Keyword result = keywordService.addKeywordToSite(site, "신규 키워드");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("신규 키워드");
        assertThat(result.getSite().getId()).isEqualTo(site.getId());

        Site updated = siteRepository.findById(site.getId()).orElseThrow();
        assertThat(updated.getDetectContentChange()).isFalse();
    }

    @Test
    @DisplayName("addKeywordToSite: detectContentChange=false 사이트에 키워드 추가 시 상태 변경 없음")
    void addKeywordToSite_WhenAlreadyInKeywordMode_ShouldNotChangeSiteState() {
        // testSite는 detectContentChange=false 로 setUp

        Keyword result = keywordService.addKeywordToSite(testSite, "추가 키워드");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getKeyword()).isEqualTo("추가 키워드");

        Site unchanged = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(unchanged.getDetectContentChange()).isFalse();
        assertThat(keywordRepository.findBySite(testSite)).hasSize(2);
    }

    @Test
    @DisplayName("addKeywordToSite: 이미 등록된 키워드 추가 시 IllegalArgumentException")
    void addKeywordToSite_WhenDuplicateKeyword_ShouldThrowIllegalArgumentException() {
        // testKeyword ("테스트")는 testSite에 이미 등록됨 (setUp)
        assertThatThrownBy(() -> keywordService.addKeywordToSite(testSite, "테스트"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 키워드입니다");
    }

    @Test
    @DisplayName("addKeywordToSite: 공백 키워드 추가 시 IllegalArgumentException")
    void addKeywordToSite_WhenBlankKeyword_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> keywordService.addKeywordToSite(testSite, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("키워드를 입력해주세요");
    }

    // ===============================
    // removeKeywordFromSite 테스트
    // ===============================

    @Test
    @DisplayName("removeKeywordFromSite: 마지막 키워드 삭제 시 detectContentChange=true 복원")
    void removeKeywordFromSite_WhenLastKeyword_ShouldRestoreContentChangeDetection() {
        // testSite에 testKeyword 1개만 존재 (setUp 기준)
        keywordService.removeKeywordFromSite(testSite, testKeyword);

        assertThat(keywordRepository.findBySite(testSite)).isEmpty();

        Site restored = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(restored.getDetectContentChange()).isTrue();
    }

    @Test
    @DisplayName("removeKeywordFromSite: 키워드가 남아있으면 detectContentChange 복원 안 함")
    void removeKeywordFromSite_WhenMoreKeywordsRemain_ShouldNotRestoreContentChangeDetection() {
        Keyword extra = keywordRepository.save(Keyword.builder()
                .keyword("추가 키워드")
                .site(testSite)
                .active(true)
                .build());

        keywordService.removeKeywordFromSite(testSite, testKeyword);

        assertThat(keywordRepository.findBySite(testSite)).hasSize(1);
        assertThat(keywordRepository.findBySite(testSite).get(0).getId()).isEqualTo(extra.getId());

        Site unchanged = siteRepository.findById(testSite.getId()).orElseThrow();
        assertThat(unchanged.getDetectContentChange()).isFalse();
    }
}
