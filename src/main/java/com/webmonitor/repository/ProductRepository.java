package com.webmonitor.repository;

import com.webmonitor.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Product 엔티티에 대한 데이터 액세스 인터페이스
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 활성화 여부로 제품 조회
     */
    List<Product> findByActive(Boolean active);

    /**
     * 우선순위로 제품 조회
     */
    List<Product> findByPriority(Product.Priority priority);

    /**
     * 활성화 + 우선순위로 제품 조회
     */
    List<Product> findByActiveAndPriority(Boolean active, Product.Priority priority);

    /**
     * 쇼핑몰 이름으로 제품 조회
     */
    List<Product> findByShopName(String shopName);

    /**
     * 제품명으로 검색 (부분 일치)
     */
    List<Product> findByNameContaining(String name);

    /**
     * URL 존재 여부 확인
     */
    boolean existsByUrl(String url);

    /**
     * 활성화된 제품 수 카운트
     */
    long countByActive(Boolean active);

    // ========================================
    // Discord User ID 기반 조회 메서드
    // ========================================

    /**
     * 디스코드 사용자 ID로 모든 제품 조회
     * @param discordUserId 디스코드 사용자 ID
     * @return 해당 사용자의 제품 목록
     */
    List<Product> findByDiscordUserId(String discordUserId);

    /**
     * 디스코드 사용자 ID와 활성화 상태로 제품 조회
     * @param discordUserId 디스코드 사용자 ID
     * @param active 활성화 여부
     * @return 해당 사용자의 활성화된 제품 목록
     */
    List<Product> findByDiscordUserIdAndActive(String discordUserId, Boolean active);

    /**
     * 디스코드 사용자 ID와 제품 이름으로 조회
     * @param discordUserId 디스코드 사용자 ID
     * @param name 제품 이름
     * @return 해당 사용자의 제품 (Optional)
     */
    List<Product> findByDiscordUserIdAndName(String discordUserId, String name);

    /**
     * 디스코드 사용자 ID와 우선순위로 제품 조회
     * @param discordUserId 디스코드 사용자 ID
     * @param priority 우선순위
     * @return 해당 사용자의 특정 우선순위 제품 목록
     */
    List<Product> findByDiscordUserIdAndPriority(String discordUserId, Product.Priority priority);

    /**
     * 디스코드 사용자 ID와 활성화 상태, 우선순위로 제품 조회
     * @param discordUserId 디스코드 사용자 ID
     * @param active 활성화 여부
     * @param priority 우선순위
     * @return 해당 사용자의 활성화된 특정 우선순위 제품 목록
     */
    List<Product> findByDiscordUserIdAndActiveAndPriority(String discordUserId, Boolean active, Product.Priority priority);

    /**
     * 디스코드 사용자 ID와 URL로 제품 존재 여부 확인 (성능 최적화)
     * @param discordUserId 디스코드 사용자 ID
     * @param url 제품 URL
     * @return 존재 여부
     */
    boolean existsByDiscordUserIdAndUrl(String discordUserId, String url);
}
