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
 * SiteRepository 통합 테스트
 * H2 in-memory 데이터베이스를 사용한 Repository 계층 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class SiteRepositoryTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    private Site testSite;
    private String testDiscordUserId = "123456789012345678";

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        siteRepository.deleteAll();
        keywordRepository.deleteAll();
    }

    @Test
    void save_shouldPersistSite() {
        // Given
        Site site = createTestSite("Test Site", "https://example.com", true);

        // When
        Site savedSite = siteRepository.save(site);

        // Then
        assertThat(savedSite.getId()).isNotNull();
        assertThat(savedSite.getName()).isEqualTo("Test Site");
        assertThat(savedSite.getUrl()).isEqualTo("https://example.com");
        assertThat(savedSite.getActive()).isTrue();
        assertThat(savedSite.getCreatedAt()).isNotNull();
        assertThat(savedSite.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnSite() {
        // Given
        Site site = createTestSite("Find Test", "https://find.com", true);
        Site saved = siteRepository.save(site);

        // When
        Optional<Site> found = siteRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Find Test");
    }

    @Test
    void findByActive_shouldReturnActiveSites() {
        // Given
        Site active1 = createTestSite("Active 1", "https://active1.com", true);
        Site active2 = createTestSite("Active 2", "https://active2.com", true);
        Site inactive = createTestSite("Inactive", "https://inactive.com", false);
        siteRepository.saveAll(List.of(active1, active2, inactive));

        // When
        List<Site> activeSites = siteRepository.findByActive(true);
        List<Site> inactiveSites = siteRepository.findByActive(false);

        // Then
        assertThat(activeSites).hasSize(2);
        assertThat(activeSites).extracting(Site::getName)
                .containsExactlyInAnyOrder("Active 1", "Active 2");
        assertThat(inactiveSites).hasSize(1);
        assertThat(inactiveSites.get(0).getName()).isEqualTo("Inactive");
    }

    @Test
    void findByActiveWithKeywords_shouldPreventNPlusOne() {
        // Given
        Site site1 = createTestSite("Site with Keywords 1", "https://keywords1.com", true);
        Site site2 = createTestSite("Site with Keywords 2", "https://keywords2.com", true);
        siteRepository.saveAll(List.of(site1, site2));

        Keyword keyword1 = createTestKeyword("keyword1", site1);
        Keyword keyword2 = createTestKeyword("keyword2", site1);
        Keyword keyword3 = createTestKeyword("keyword3", site2);
        keywordRepository.saveAll(List.of(keyword1, keyword2, keyword3));

        // When
        List<Site> sites = siteRepository.findByActiveWithKeywords(true);

        // Then
        assertThat(sites).hasSize(2);
        // 키워드가 즉시 로딩되었는지 확인 (Lazy Loading Exception 없이 접근 가능)
        assertThat(sites.get(0).getKeywords()).isNotNull();
        assertThat(sites.get(1).getKeywords()).isNotNull();
    }

    @Test
    void findByNameContaining_shouldReturnMatchingSites() {
        // Given
        Site site1 = createTestSite("Example Site One", "https://example1.com", true);
        Site site2 = createTestSite("Example Site Two", "https://example2.com", true);
        Site site3 = createTestSite("Different Site", "https://different.com", true);
        siteRepository.saveAll(List.of(site1, site2, site3));

        // When
        List<Site> exampleSites = siteRepository.findByNameContaining("Example");

        // Then
        assertThat(exampleSites).hasSize(2);
        assertThat(exampleSites).extracting(Site::getName)
                .containsExactlyInAnyOrder("Example Site One", "Example Site Two");
    }

    @Test
    void findByUrl_shouldReturnExactMatch() {
        // Given
        Site site1 = createTestSite("Site 1", "https://unique.com", true);
        Site site2 = createTestSite("Site 2", "https://another.com", true);
        siteRepository.saveAll(List.of(site1, site2));

        // When
        List<Site> found = siteRepository.findByUrl("https://unique.com");

        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("Site 1");
    }

    @Test
    void countByActive_shouldReturnCorrectCount() {
        // Given
        Site active1 = createTestSite("Active 1", "https://active1.com", true);
        Site active2 = createTestSite("Active 2", "https://active2.com", true);
        Site inactive = createTestSite("Inactive", "https://inactive.com", false);
        siteRepository.saveAll(List.of(active1, active2, inactive));

        // When
        long activeCount = siteRepository.countByActive(true);
        long inactiveCount = siteRepository.countByActive(false);

        // Then
        assertThat(activeCount).isEqualTo(2);
        assertThat(inactiveCount).isEqualTo(1);
    }

    @Test
    void findByDiscordUserId_shouldReturnUserSites() {
        // Given
        String userId1 = "111111111111111111";
        String userId2 = "222222222222222222";

        Site user1Site1 = createTestSiteWithUser("User1 Site1", "https://user1-1.com", true, userId1);
        Site user1Site2 = createTestSiteWithUser("User1 Site2", "https://user1-2.com", true, userId1);
        Site user2Site = createTestSiteWithUser("User2 Site", "https://user2.com", true, userId2);
        siteRepository.saveAll(List.of(user1Site1, user1Site2, user2Site));

        // When
        List<Site> user1Sites = siteRepository.findByDiscordUserId(userId1);
        List<Site> user2Sites = siteRepository.findByDiscordUserId(userId2);

        // Then
        assertThat(user1Sites).hasSize(2);
        assertThat(user1Sites).extracting(Site::getName)
                .containsExactlyInAnyOrder("User1 Site1", "User1 Site2");
        assertThat(user2Sites).hasSize(1);
        assertThat(user2Sites.get(0).getName()).isEqualTo("User2 Site");
    }

    @Test
    void findByDiscordUserIdAndActive_shouldReturnActiveUserSites() {
        // Given
        String userId = "333333333333333333";

        Site active = createTestSiteWithUser("Active User Site", "https://active.com", true, userId);
        Site inactive = createTestSiteWithUser("Inactive User Site", "https://inactive.com", false, userId);
        siteRepository.saveAll(List.of(active, inactive));

        // When
        List<Site> activeSites = siteRepository.findByDiscordUserIdAndActive(userId, true);
        List<Site> inactiveSites = siteRepository.findByDiscordUserIdAndActive(userId, false);

        // Then
        assertThat(activeSites).hasSize(1);
        assertThat(activeSites.get(0).getName()).isEqualTo("Active User Site");
        assertThat(inactiveSites).hasSize(1);
        assertThat(inactiveSites.get(0).getName()).isEqualTo("Inactive User Site");
    }

    @Test
    void findByDiscordUserIdAndName_shouldReturnMatchingSite() {
        // Given
        String userId = "444444444444444444";

        Site site1 = createTestSiteWithUser("My Site", "https://mysite.com", true, userId);
        Site site2 = createTestSiteWithUser("Other Site", "https://other.com", true, userId);
        siteRepository.saveAll(List.of(site1, site2));

        // When
        List<Site> found = siteRepository.findByDiscordUserIdAndName(userId, "My Site");

        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("My Site");
        assertThat(found.get(0).getDiscordUserId()).isEqualTo(userId);
    }

    @Test
    void findByDiscordUserIdAndActiveWithKeywords_shouldPreventNPlusOne() {
        // Given
        String userId = "555555555555555555";

        Site site1 = createTestSiteWithUser("User Site 1", "https://user1.com", true, userId);
        Site site2 = createTestSiteWithUser("User Site 2", "https://user2.com", true, userId);
        Site inactiveSite = createTestSiteWithUser("Inactive Site", "https://inactive.com", false, userId);
        siteRepository.saveAll(List.of(site1, site2, inactiveSite));

        Keyword keyword1 = createTestKeyword("keyword1", site1);
        Keyword keyword2 = createTestKeyword("keyword2", site2);
        keywordRepository.saveAll(List.of(keyword1, keyword2));

        // When
        List<Site> sites = siteRepository.findByDiscordUserIdAndActiveWithKeywords(userId, true);

        // Then
        assertThat(sites).hasSize(2);
        assertThat(sites).extracting(Site::getName)
                .containsExactlyInAnyOrder("User Site 1", "User Site 2");
        // 키워드가 즉시 로딩되었는지 확인
        assertThat(sites.get(0).getKeywords()).isNotNull();
        assertThat(sites.get(1).getKeywords()).isNotNull();
    }

    @Test
    void existsByDiscordUserIdAndUrl_shouldCheckExistence() {
        // Given
        String userId = "666666666666666666";
        String existingUrl = "https://existing.com";
        String nonExistingUrl = "https://nonexisting.com";

        Site site = createTestSiteWithUser("Existing Site", existingUrl, true, userId);
        siteRepository.save(site);

        // When
        boolean exists = siteRepository.existsByDiscordUserIdAndUrl(userId, existingUrl);
        boolean notExists = siteRepository.existsByDiscordUserIdAndUrl(userId, nonExistingUrl);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void delete_shouldRemoveSite() {
        // Given
        Site site = createTestSite("To Delete", "https://delete.com", true);
        Site saved = siteRepository.save(site);

        // When
        siteRepository.deleteById(saved.getId());

        // Then
        assertThat(siteRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void update_shouldModifySite() {
        // Given
        Site site = createTestSite("Original Name", "https://original.com", true);
        Site saved = siteRepository.save(site);

        // When
        saved.setName("Updated Name");
        saved.setActive(false);
        Site updated = siteRepository.save(saved);

        // Then
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
    }

    @Test
    void save_withCheckIntervalMinutes_shouldRespectDefaultValue() {
        // Given
        Site site = Site.builder()
                .name("Default Interval Site")
                .url("https://default.com")
                .active(true)
                .build();

        // When
        Site saved = siteRepository.save(site);

        // Then
        assertThat(saved.getCheckIntervalMinutes()).isEqualTo(1); // 기본값 확인
    }

    @Test
    void save_withCustomCheckInterval_shouldPersistValue() {
        // Given
        Site site = Site.builder()
                .name("Custom Interval Site")
                .url("https://custom.com")
                .active(true)
                .checkIntervalMinutes(10)
                .build();

        // When
        Site saved = siteRepository.save(site);

        // Then
        assertThat(saved.getCheckIntervalMinutes()).isEqualTo(10);
    }

    /**
     * 테스트용 Site 생성 헬퍼 메서드
     */
    private Site createTestSite(String name, String url, Boolean active) {
        return Site.builder()
                .name(name)
                .url(url)
                .active(active)
                .checkIntervalMinutes(5)
                .build();
    }

    /**
     * 디스코드 사용자 ID를 포함한 테스트용 Site 생성 헬퍼 메서드
     */
    private Site createTestSiteWithUser(String name, String url, Boolean active, String discordUserId) {
        return Site.builder()
                .name(name)
                .url(url)
                .active(active)
                .checkIntervalMinutes(5)
                .discordUserId(discordUserId)
                .build();
    }

    /**
     * 테스트용 Keyword 생성 헬퍼 메서드
     */
    private Keyword createTestKeyword(String keyword, Site site) {
        return Keyword.builder()
                .keyword(keyword)
                .site(site)
                .active(true)
                .build();
    }
}
