package com.webmonitor.repository;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Keyword 엔티티에 대한 데이터베이스 접근을 담당하는 Repository
 * JpaRepository를 상속받아 기본 CRUD 메서드 제공
 */
@Repository // Spring의 Repository 컴포넌트로 등록
public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    /**
     * 특정 사이트의 모든 키워드 조회
     * @param site 사이트 엔티티
     * @return 해당 사이트의 키워드 목록
     */
    List<Keyword> findBySite(Site site);

    /**
     * 특정 사이트의 활성화된 키워드 조회
     * @param site 사이트 엔티티
     * @param active 활성화 여부
     * @return 해당 사이트의 활성화된 키워드 목록
     */
    List<Keyword> findBySiteAndActive(Site site, Boolean active);

    /**
     * 활성화 상태로 모든 키워드 조회
     * @param active 활성화 여부
     * @return 활성화된 키워드 목록
     */
    List<Keyword> findByActive(Boolean active);

    /**
     * 키워드 텍스트로 검색
     * @param keyword 키워드 텍스트
     * @return 일치하는 키워드 목록
     */
    List<Keyword> findByKeywordContaining(String keyword);
}
