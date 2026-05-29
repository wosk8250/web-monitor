package com.webmonitor.repository;

import com.webmonitor.domain.Site;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Site 엔티티에 대한 데이터베이스 접근을 담당하는 Repository
 * JpaRepository를 상속받아 기본 CRUD 메서드 제공
 */
@Repository // Spring의 Repository 컴포넌트로 등록
public interface SiteRepository extends JpaRepository<Site, Long> {

    /**
     * 활성화 상태로 모든 사이트 조회
     * @param active 활성화 여부
     * @return 활성화된 사이트 목록
     */
    List<Site> findByActive(Boolean active);

    /**
     * 활성화 상태로 모든 사이트 조회 (키워드 포함, N+1 쿼리 방지)
     * MonitorService에서 사용 - 키워드 접근 시 추가 쿼리 없이 한 번에 로딩
     * @param active 활성화 여부
     * @return 활성화된 사이트 목록 (keywords 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"keywords"})
    @Query("SELECT s FROM Site s WHERE s.active = :active")
    List<Site> findByActiveWithKeywords(@Param("active") Boolean active);

    /**
     * 사이트 이름으로 조회
     * @param name 사이트 이름
     * @return 해당 이름의 사이트 목록
     */
    List<Site> findByNameContaining(String name);

    /**
     * URL로 사이트 조회
     * @param url 사이트 URL
     * @return URL이 일치하는 사이트 목록
     */
    List<Site> findByUrl(String url);

    /**
     * 활성화 상태별 사이트 개수 조회
     * @param active 활성화 여부
     * @return 해당 상태의 사이트 개수
     */
    long countByActive(Boolean active);

    // ========================================
    // Discord User ID 기반 조회 메서드
    // ========================================

    /**
     * 디스코드 사용자 ID로 모든 사이트 조회
     * @param discordUserId 디스코드 사용자 ID
     * @return 해당 사용자의 사이트 목록
     */
    List<Site> findByDiscordUserId(String discordUserId);

    /**
     * 디스코드 사용자 ID와 활성화 상태로 사이트 조회
     * @param discordUserId 디스코드 사용자 ID
     * @param active 활성화 여부
     * @return 해당 사용자의 활성화된 사이트 목록
     */
    List<Site> findByDiscordUserIdAndActive(String discordUserId, Boolean active);

    /**
     * 디스코드 사용자 ID와 사이트 이름으로 조회
     * @param discordUserId 디스코드 사용자 ID
     * @param name 사이트 이름
     * @return 해당 사용자의 사이트 (Optional)
     */
    List<Site> findByDiscordUserIdAndName(String discordUserId, String name);

    /**
     * 디스코드 사용자 ID로 사이트 조회 (키워드 포함, N+1 쿼리 방지)
     * @param discordUserId 디스코드 사용자 ID
     * @param active 활성화 여부
     * @return 해당 사용자의 활성화된 사이트 목록 (keywords 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"keywords"})
    @Query("SELECT s FROM Site s WHERE s.discordUserId = :discordUserId AND s.active = :active")
    List<Site> findByDiscordUserIdAndActiveWithKeywords(@Param("discordUserId") String discordUserId, @Param("active") Boolean active);

    /**
     * 디스코드 사용자 ID와 URL로 사이트 존재 여부 확인 (성능 최적화)
     * @param discordUserId 디스코드 사용자 ID
     * @param url 사이트 URL
     * @return 존재 여부
     */
    boolean existsByDiscordUserIdAndUrl(String discordUserId, String url);
}
