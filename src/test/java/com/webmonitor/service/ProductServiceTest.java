package com.webmonitor.service;

import com.webmonitor.domain.Product;
import com.webmonitor.domain.Product.StockStatus;
import com.webmonitor.repository.ProductRepository;
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

/**
 * ProductService 통합 테스트
 */
@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        productRepository.deleteAll();

        // 테스트용 제품 생성 및 저장
        testProduct = Product.builder()
                .name("테스트 제품")
                .url("https://test.com/product")
                .active(true)
                .notifyOnRestock(true)
                .priority(Product.Priority.NORMAL)
                .checkIntervalMinutes(5)
                .currentStatus(StockStatus.IN_STOCK)
                .build();
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("제품 등록 성공")
    void createProduct_Success() {
        // Given
        Product newProduct = Product.builder()
                .name("새 제품")
                .url("https://test.com/new-product")
                .active(true)
                .notifyOnRestock(false)
                .priority(Product.Priority.URGENT)
                .checkIntervalMinutes(10)
                .build();

        // When
        Product created = productService.createProduct(newProduct);

        // Then
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("새 제품");
        assertThat(created.getUrl()).isEqualTo("https://test.com/new-product");
        assertThat(created.getPriority()).isEqualTo(Product.Priority.URGENT);
        assertThat(productRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("제품 수정 성공")
    void updateProduct_Success() {
        // Given
        Product updatedData = Product.builder()
                .name("수정된 제품명")
                .url("https://test.com/updated")
                .active(false)
                .notifyOnRestock(false)
                .priority(Product.Priority.URGENT)
                .checkIntervalMinutes(15)
                .build();

        // When
        Product updated = productService.updateProduct(testProduct.getId(), updatedData);

        // Then
        assertThat(updated.getName()).isEqualTo("수정된 제품명");
        assertThat(updated.getUrl()).isEqualTo("https://test.com/updated");
        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getPriority()).isEqualTo(Product.Priority.URGENT);
        assertThat(updated.getCheckIntervalMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("제품 수정 - notifyOnContentChange 업데이트 확인")
    void updateProduct_updatesNotifyOnContentChange() {
        // Given
        Product updatedData = Product.builder()
                .name("수정된 제품명")
                .url("https://test.com/updated")
                .active(true)
                .notifyOnRestock(true)
                .notifyOnContentChange(false)
                .priority(Product.Priority.NORMAL)
                .checkIntervalMinutes(5)
                .build();

        // When
        Product updated = productService.updateProduct(testProduct.getId(), updatedData);

        // Then
        assertThat(updated.getNotifyOnContentChange()).isFalse();
    }

    @Test
    @DisplayName("제품 수정 - notifyOnContentChange가 null이면 기존값 유지")
    void updateProduct_nullNotifyOnContentChange_keepsExistingValue() {
        // Given: testProduct의 notifyOnContentChange = true (builder default)
        Product updatedData = Product.builder()
                .name("수정된 제품명")
                .url("https://test.com/updated")
                .active(true)
                .notifyOnRestock(true)
                .priority(Product.Priority.NORMAL)
                .checkIntervalMinutes(5)
                .build();
        updatedData.setNotifyOnContentChange(null);

        // When
        Product updated = productService.updateProduct(testProduct.getId(), updatedData);

        // Then: null이 전달되어도 기존값(true)이 유지됨
        assertThat(updated.getNotifyOnContentChange()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 제품 수정 시 예외 발생")
    void updateProduct_NotFound_ThrowsException() {
        // Given
        Long nonExistentId = 99999L;
        Product updatedData = Product.builder().name("수정").url("https://test.com").build();

        // When & Then
        assertThatThrownBy(() -> productService.updateProduct(nonExistentId, updatedData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("제품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("제품 삭제 성공")
    void deleteProduct_Success() {
        // When
        productService.deleteProduct(testProduct.getId());

        // Then
        assertThat(productRepository.count()).isEqualTo(0);
        assertThat(productRepository.findById(testProduct.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 제품 삭제 시 예외 발생")
    void deleteProduct_NotFound_ThrowsException() {
        // Given
        Long nonExistentId = 99999L;

        // When & Then
        assertThatThrownBy(() -> productService.deleteProduct(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("제품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("ID로 제품 조회 성공")
    void getProductById_Success() {
        // When
        Optional<Product> found = productService.getProductById(testProduct.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트 제품");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 Optional 반환")
    void getProductById_NotFound_ReturnsEmpty() {
        // Given
        Long nonExistentId = 99999L;

        // When
        Optional<Product> found = productService.getProductById(nonExistentId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("모든 제품 조회")
    void getAllProducts_ReturnsAll() {
        // Given
        Product product2 = Product.builder()
                .name("제품 2")
                .url("https://test.com/product2")
                .active(false)
                .build();
        productRepository.save(product2);

        // When
        List<Product> products = productService.getAllProducts();

        // Then
        assertThat(products).hasSize(2);
    }

    @Test
    @DisplayName("활성화된 제품만 조회")
    void getActiveProducts_ReturnsOnlyActive() {
        // Given
        Product inactiveProduct = Product.builder()
                .name("비활성 제품")
                .url("https://test.com/inactive")
                .active(false)
                .build();
        productRepository.save(inactiveProduct);

        // When
        List<Product> activeProducts = productService.getActiveProducts();

        // Then
        assertThat(activeProducts).hasSize(1);
        assertThat(activeProducts.get(0).getActive()).isTrue();
    }

    @Test
    @DisplayName("우선순위별 활성 제품 조회")
    void getActiveProductsByPriority_ReturnsCorrectProducts() {
        // Given
        Product urgentProduct = Product.builder()
                .name("긴급 제품")
                .url("https://test.com/urgent")
                .active(true)
                .priority(Product.Priority.URGENT)
                .build();
        productRepository.save(urgentProduct);

        // When
        List<Product> urgentProducts = productService.getActiveProductsByPriority(Product.Priority.URGENT);
        List<Product> normalProducts = productService.getActiveProductsByPriority(Product.Priority.NORMAL);

        // Then
        assertThat(urgentProducts).hasSize(1);
        assertThat(urgentProducts.get(0).getPriority()).isEqualTo(Product.Priority.URGENT);
        assertThat(normalProducts).hasSize(1);
        assertThat(normalProducts.get(0).getPriority()).isEqualTo(Product.Priority.NORMAL);
    }

    @Test
    @DisplayName("제품명으로 검색")
    void searchProductsByName_ReturnsMatchingProducts() {
        // Given
        Product product2 = Product.builder()
                .name("다른 제품")
                .url("https://test.com/other")
                .active(true)
                .build();
        productRepository.save(product2);

        // When
        List<Product> results = productService.searchProductsByName("테스트");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).contains("테스트");
    }

    @Test
    @DisplayName("제품 활성화/비활성화 토글")
    void toggleProductActive_Success() {
        // Given
        boolean initialActive = testProduct.getActive();

        // When
        Product toggled = productService.toggleProductActive(testProduct.getId());

        // Then
        assertThat(toggled.getActive()).isEqualTo(!initialActive);
    }

    @Test
    @DisplayName("제품 우선순위 변경 성공")
    void setProductPriority_Success() {
        // When
        Product updated = productService.setProductPriority(testProduct.getId(), Product.Priority.URGENT);

        // Then
        assertThat(updated.getPriority()).isEqualTo(Product.Priority.URGENT);
    }

    @Test
    @DisplayName("제품 체크 주기 설정 성공")
    void setProductCheckInterval_Success() {
        // When
        Product updated = productService.setProductCheckInterval(testProduct.getId(), 20);

        // Then
        assertThat(updated.getCheckIntervalMinutes()).isEqualTo(20);
    }

    @Test
    @DisplayName("체크 주기를 1분 미만으로 설정 시 예외 발생")
    void setProductCheckInterval_LessThanOne_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> productService.setProductCheckInterval(testProduct.getId(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("체크 주기는 최소 1분이어야 합니다");
    }

    @Test
    @DisplayName("URL 중복 확인 - 중복 있음")
    void isUrlDuplicate_Exists_ReturnsTrue() {
        // When
        boolean isDuplicate = productService.isUrlDuplicate("https://test.com/product");

        // Then
        assertThat(isDuplicate).isTrue();
    }

    @Test
    @DisplayName("URL 중복 확인 - 중복 없음")
    void isUrlDuplicate_NotExists_ReturnsFalse() {
        // When
        boolean isDuplicate = productService.isUrlDuplicate("https://test.com/unique");

        // Then
        assertThat(isDuplicate).isFalse();
    }
}
