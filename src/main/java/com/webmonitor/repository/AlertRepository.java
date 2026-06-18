package com.webmonitor.repository;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Alert 엔티티에 대한 데이터베이스 접근을 담당하는 Repository
 * JpaRepository를 상속받아 기본 CRUD 메서드 제공
 */
@Repository // Spring의 Repository 컴포넌트로 등록
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * 특정 사이트의 모든 알림 조회
     * @param site 사이트 엔티티
     * @return 해당 사이트의 알림 목록
     */
    List<Alert> findBySite(Site site);

    /**
     * 특정 키워드의 모든 알림 조회
     * @param keyword 키워드 엔티티
     * @return 해당 키워드의 알림 목록
     */
    List<Alert> findByKeyword(Keyword keyword);

    /**
     * 전송되지 않은 알림 조회
     * @param sent 전송 여부
     * @return 미전송 알림 목록
     */
    List<Alert> findBySent(Boolean sent);

    /**
     * 특정 기간 동안의 알림 조회 (N+1 쿼리 방지)
     * @param startDate 시작 시간
     * @param endDate 종료 시간
     * @return 해당 기간의 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findByDetectedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 특정 사이트의 미전송 알림 조회
     * @param site 사이트 엔티티
     * @param sent 전송 여부
     * @return 해당 사이트의 미전송 알림 목록
     */
    List<Alert> findBySiteAndSent(Site site, Boolean sent);

    /**
     * 특정 사이트의 모든 알림 삭제
     * @param siteId 사이트 ID
     */
    void deleteBySiteId(Long siteId);

    /**
     * 모든 알림을 최신순으로 조회 (detectedAt 기준 내림차순, N+1 쿼리 방지)
     * @return 최신순으로 정렬된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findAllByOrderByDetectedAtDesc();

    /**
     * 전송 여부별 알림을 최신순으로 조회 (N+1 쿼리 방지)
     * @param sent 전송 여부
     * @return 최신순으로 정렬된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findBySentOrderByDetectedAtDesc(Boolean sent);

    /**
     * 전송 여부별 알림을 우선순위 → 최신순으로 조회 (N+1 쿼리 방지)
     * @param sent 전송 여부
     * @return 우선순위 내림차순, 최신순으로 정렬된 알림 목록
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findBySentOrderByPriorityDescDetectedAtDesc(Boolean sent);

    /**
     * 특정 사이트의 알림 개수 조회
     * @param site 사이트 엔티티
     * @return 해당 사이트의 알림 개수
     */
    long countBySite(Site site);

    /**
     * 특정 사이트의 알림을 오래된 순으로 조회
     * @param site 사이트 엔티티
     * @return 오래된 순으로 정렬된 알림 목록
     */
    List<Alert> findBySiteOrderByDetectedAtAsc(Site site);

    /**
     * 특정 시간 이후의 알림 개수 조회
     * @param dateTime 기준 시간
     * @return 기준 시간 이후 알림 개수
     */
    long countByDetectedAtAfter(LocalDateTime dateTime);

    /**
     * 최근 알림 조회 (개수 제한)
     * @return 최신순 알림 목록 (최대 5개)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findTop5ByOrderByDetectedAtDesc();

    /**
     * 특정 사이트 ID의 알림을 최신순으로 조회 (N+1 쿼리 방지)
     * @param siteId 사이트 ID
     * @return 최신순으로 정렬된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findBySiteIdOrderByDetectedAtDesc(Long siteId);

    /**
     * 특정 키워드 ID의 알림을 최신순으로 조회 (N+1 쿼리 방지)
     * @param keywordId 키워드 ID
     * @return 최신순으로 정렬된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    List<Alert> findByKeywordIdOrderByDetectedAtDesc(Long keywordId);

    /**
     * 특정 제품 ID의 알림을 최신순으로 조회 (N+1 쿼리 방지)
     * @param productId 제품 ID
     * @return 최신순으로 정렬된 알림 목록
     */
    List<Alert> findByProductIdOrderByDetectedAtDesc(Long productId);

    // ==================== 페이지네이션 메서드 ====================

    /**
     * 모든 알림 조회 (페이지네이션, 최신순, N+1 쿼리 방지)
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    Page<Alert> findAllByOrderByDetectedAtDesc(Pageable pageable);

    /**
     * 전송 여부별 알림 조회 (페이지네이션, 우선순위 + 최신순, N+1 쿼리 방지)
     * 우선순위가 높은 순서대로 먼저 표시
     * @param sent 전송 여부
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    Page<Alert> findBySentOrderByPriorityDescDetectedAtDesc(Boolean sent, Pageable pageable);

    /**
     * 전송 여부 및 우선순위로 알림 조회 (페이지네이션, 최신순)
     * AlertQueueProcessor에서 NORMAL 우선순위 알림만 처리하기 위해 사용
     * @param sent 전송 여부
     * @param priority 우선순위
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록
     */
    Page<Alert> findBySentAndPriorityOrderByDetectedAtDesc(Boolean sent, Alert.Priority priority, Pageable pageable);

    /**
     * 전송 여부, 우선순위, 재시도 횟수로 알림 조회 (페이지네이션, 최신순)
     * AlertQueueProcessor에서 재시도 가능한 알림만 조회하기 위해 사용
     * @param sent 전송 여부
     * @param priority 우선순위
     * @param retryCount 최대 재시도 횟수 미만
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록
     */
    Page<Alert> findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
            Boolean sent,
            Alert.Priority priority,
            Integer retryCount,
            Pageable pageable
    );

    /**
     * 전송 여부별 알림 조회 (페이지네이션, 최신순, N+1 쿼리 방지)
     * @param sent 전송 여부
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    Page<Alert> findBySentOrderByDetectedAtDesc(Boolean sent, Pageable pageable);

    /**
     * 특정 사이트의 알림 조회 (페이지네이션, 최신순, N+1 쿼리 방지)
     * @param siteId 사이트 ID
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    Page<Alert> findBySiteIdOrderByDetectedAtDesc(Long siteId, Pageable pageable);

    /**
     * 특정 키워드의 알림 조회 (페이지네이션, 최신순, N+1 쿼리 방지)
     * @param keywordId 키워드 ID
     * @param pageable 페이지 정보
     * @return 페이지네이션된 알림 목록 (site, keyword, product 즉시 로딩)
     */
    @EntityGraph(attributePaths = {"site", "keyword", "product"})
    Page<Alert> findByKeywordIdOrderByDetectedAtDesc(Long keywordId, Pageable pageable);

    /**
     * 특정 사이트의 가장 오래된 알림 조회 (표준 JPA 방식, Pageable 사용)
     * 배치 삭제가 필요한 경우 이 메서드로 조회 후 deleteAll() 사용 권장
     * @param site 사이트 엔티티
     * @param pageable 페이지 정보 (조회/삭제할 개수)
     * @return 오래된 순으로 정렬된 알림 페이지
     */
    Page<Alert> findBySiteOrderByDetectedAtAsc(Site site, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Alert a SET a.sent = true, a.sentAt = :sentAt WHERE a.id IN :ids")
    int markAsSentByIds(@Param("ids") List<Long> ids, @Param("sentAt") LocalDateTime sentAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Alert a SET a.keyword = null WHERE a.keyword.id = :keywordId")
    void clearKeywordReference(@Param("keywordId") Long keywordId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Alert a SET a.product = null WHERE a.product.id = :productId")
    void clearProductReference(@Param("productId") Long productId);

    @Query("SELECT a.site FROM Alert a WHERE a.site IS NOT NULL GROUP BY a.site HAVING COUNT(a) > :maxAlerts")
    List<com.webmonitor.domain.Site> findSitesWithAlertCountExceeding(@Param("maxAlerts") long maxAlerts);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Alert a WHERE a.alertType = :alertType AND a.detectedAt < :cutoffDate")
    int deleteByAlertTypeAndDetectedAtBefore(
            @Param("alertType") Alert.AlertType alertType,
            @Param("cutoffDate") LocalDateTime cutoffDate);
}
