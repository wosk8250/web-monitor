package com.webmonitor.repository;

import com.webmonitor.domain.Article;
import com.webmonitor.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Article 엔티티에 대한 데이터베이스 접근을 위한 Repository
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * 특정 사이트와 게시글 URL로 게시글 조회
     * @param site 사이트
     * @param articleUrl 게시글 URL
     * @return 게시글 (Optional)
     */
    Optional<Article> findBySiteAndArticleUrl(Site site, String articleUrl);

    /**
     * 특정 사이트와 게시글 ID로 게시글 조회
     * @param site 사이트
     * @param articleId 게시글 ID
     * @return 게시글 (Optional)
     */
    Optional<Article> findBySiteAndArticleId(Site site, String articleId);

    /**
     * 특정 사이트의 모든 게시글 조회
     * @param site 사이트
     * @return 게시글 목록
     */
    List<Article> findBySite(Site site);

    /**
     * 특정 사이트와 게시글 URL로 존재 여부 확인
     * @param site 사이트
     * @param articleUrl 게시글 URL
     * @return 존재 여부
     */
    boolean existsBySiteAndArticleUrl(Site site, String articleUrl);

    /**
     * 특정 사이트와 게시글 ID로 존재 여부 확인
     * @param site 사이트
     * @param articleId 게시글 ID
     * @return 존재 여부
     */
    boolean existsBySiteAndArticleId(Site site, String articleId);

    /**
     * 특정 사이트의 모든 게시글 삭제
     * @param site 사이트
     */
    void deleteBySite(Site site);
}
