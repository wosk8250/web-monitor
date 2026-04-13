package com.webmonitor.repository;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * 특정 기간 동안의 알림 조회
     * @param startDate 시작 시간
     * @param endDate 종료 시간
     * @return 해당 기간의 알림 목록
     */
    List<Alert> findByDetectedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 특정 사이트의 미전송 알림 조회
     * @param site 사이트 엔티티
     * @param sent 전송 여부
     * @return 해당 사이트의 미전송 알림 목록
     */
    List<Alert> findBySiteAndSent(Site site, Boolean sent);
}
