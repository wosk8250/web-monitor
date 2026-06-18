package com.webmonitor.service;

import com.webmonitor.domain.Product;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 제품 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final AlertRepository alertRepository;

    /**
     * 새로운 제품 등록
     */
    @Transactional
    @CacheEvict(value = {"products", "allProducts", "activeProducts"}, allEntries = true)
    public Product createProduct(Product product) {
        log.info("제품 등록 시작: {}", product.getName());
        Product savedProduct = productRepository.save(product);
        log.info("제품 등록 완료: ID = {}", savedProduct.getId());
        return savedProduct;
    }

    /**
     * 제품 정보 수정
     */
    @Transactional
    @CacheEvict(value = {"products", "allProducts", "activeProducts"}, allEntries = true)
    public Product updateProduct(Long id, Product updatedProduct) {
        log.info("제품 수정 시작: ID = {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + id));

        // 제품 정보 업데이트
        product.setName(updatedProduct.getName());
        product.setUrl(updatedProduct.getUrl());
        product.setActive(updatedProduct.getActive());
        product.setNotifyOnRestock(updatedProduct.getNotifyOnRestock());
        if (updatedProduct.getNotifyOnContentChange() != null) {
            product.setNotifyOnContentChange(updatedProduct.getNotifyOnContentChange());
        }
        product.setPriority(updatedProduct.getPriority());
        product.setCheckIntervalMinutes(updatedProduct.getCheckIntervalMinutes());

        Product saved = productRepository.save(product);
        log.info("제품 수정 완료: ID = {}", id);
        return saved;
    }

    /**
     * 제품 삭제
     */
    @Transactional
    @CacheEvict(value = {"products", "allProducts", "activeProducts"}, allEntries = true)
    public void deleteProduct(Long id) {
        log.info("제품 삭제 시작: ID = {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + id));

        alertRepository.clearProductReference(id);
        productRepository.delete(product);
        log.info("제품 삭제 완료: ID = {}", id);
    }

    /**
     * ID로 제품 조회 (캐시 적용)
     */
    @Cacheable(value = "products", key = "#id")
    public Optional<Product> getProductById(Long id) {
        log.debug("제품 조회 (DB): ID = {}", id);
        return productRepository.findById(id);
    }

    /**
     * 모든 제품 조회 (캐시 적용)
     */
    @Cacheable(value = "allProducts")
    public List<Product> getAllProducts() {
        log.debug("전체 제품 조회 (DB)");
        return productRepository.findAll();
    }

    /**
     * 활성화된 제품만 조회 (캐시 적용)
     */
    @Cacheable(value = "activeProducts")
    public List<Product> getActiveProducts() {
        log.debug("활성화된 제품 조회 (DB)");
        return productRepository.findByActive(true);
    }

    /**
     * 우선순위별 활성 제품 조회
     */
    public List<Product> getActiveProductsByPriority(Product.Priority priority) {
        log.debug("우선순위별 활성 제품 조회: {}", priority);
        return productRepository.findByActiveAndPriority(true, priority);
    }

    /**
     * 제품명으로 검색
     */
    public List<Product> searchProductsByName(String name) {
        log.debug("제품 이름 검색: {}", name);
        return productRepository.findByNameContaining(name);
    }

    /**
     * 제품 활성화/비활성화 토글
     */
    @Transactional
    @CacheEvict(value = {"products", "allProducts", "activeProducts"}, allEntries = true)
    public Product toggleProductActive(Long id) {
        log.info("제품 활성화 상태 변경: ID = {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + id));

        product.setActive(!product.getActive());
        Product saved = productRepository.save(product);

        log.info("제품 활성화 상태 변경 완료: ID = {}, Active = {}", id, saved.getActive());
        return saved;
    }

    /**
     * 제품 우선순위 변경
     */
    @Transactional
    @CacheEvict(value = {"products", "allProducts", "activeProducts"}, allEntries = true)
    public Product setProductPriority(Long id, Product.Priority priority) {
        log.info("제품 우선순위 변경: ID = {}, Priority = {}", id, priority);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + id));

        product.setPriority(priority);
        Product saved = productRepository.save(product);

        log.info("제품 우선순위 변경 완료: ID = {}, Priority = {}", id, priority);
        return saved;
    }

    /**
     * 제품 체크 주기 설정
     */
    @Transactional
    @CacheEvict(value = {"products", "allProducts", "activeProducts"}, allEntries = true)
    public Product setProductCheckInterval(Long id, Integer intervalMinutes) {
        log.info("제품 체크 주기 설정: ID = {}, Interval = {}분", id, intervalMinutes);

        if (intervalMinutes < 1) {
            throw new IllegalArgumentException("체크 주기는 최소 1분이어야 합니다.");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다. ID: " + id));

        product.setCheckIntervalMinutes(intervalMinutes);
        Product saved = productRepository.save(product);

        log.info("제품 체크 주기 설정 완료: ID = {}, Interval = {}분", id, intervalMinutes);
        return saved;
    }

    /**
     * URL 중복 확인
     */
    public boolean isUrlDuplicate(String url) {
        return productRepository.existsByUrl(url);
    }
}
