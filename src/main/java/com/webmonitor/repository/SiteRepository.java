package com.webmonitor.repository;

import com.webmonitor.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
