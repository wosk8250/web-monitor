package com.webmonitor.repository;

import com.webmonitor.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductRepository 통합 테스트
 * H2 in-memory 데이터베이스를 사용한 Repository 계층 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void save_shouldPersistProduct() {
        // Given
        Product product = createTestProduct("Test Product", "https://test.com/product1", true, Product.Priority.NORMAL, null);

        // When
        Product saved = productRepository.save(product);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Test Product");
        assertThat(saved.getUrl()).isEqualTo("https://test.com/product1");
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getPriority()).isEqualTo(Product.Priority.NORMAL);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnProduct() {
        // Given
        Product product = createTestProduct("Find Product", "https://find.com/product", true, Product.Priority.NORMAL, null);
        Product saved = productRepository.save(product);

        // When
        Optional<Product> found = productRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Find Product");
    }

    @Test
    void findByActive_shouldReturnActiveProducts() {
        // Given
        Product active1 = createTestProduct("Active 1", "https://active1.com", true, Product.Priority.NORMAL, null);
        Product active2 = createTestProduct("Active 2", "https://active2.com", true, Product.Priority.NORMAL, null);
        Product inactive = createTestProduct("Inactive", "https://inactive.com", false, Product.Priority.NORMAL, null);
        productRepository.saveAll(List.of(active1, active2, inactive));

        // When
        List<Product> activeProducts = productRepository.findByActive(true);
        List<Product> inactiveProducts = productRepository.findByActive(false);

        // Then
        assertThat(activeProducts).hasSize(2);
        assertThat(activeProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Active 1", "Active 2");
        assertThat(inactiveProducts).hasSize(1);
        assertThat(inactiveProducts.get(0).getName()).isEqualTo("Inactive");
    }

    @Test
    void findByPriority_shouldReturnProductsByPriority() {
        // Given
        Product urgent1 = createTestProduct("Urgent 1", "https://urgent1.com", true, Product.Priority.URGENT, null);
        Product urgent2 = createTestProduct("Urgent 2", "https://urgent2.com", true, Product.Priority.URGENT, null);
        Product normal = createTestProduct("Normal", "https://normal.com", true, Product.Priority.NORMAL, null);
        productRepository.saveAll(List.of(urgent1, urgent2, normal));

        // When
        List<Product> urgentProducts = productRepository.findByPriority(Product.Priority.URGENT);
        List<Product> normalProducts = productRepository.findByPriority(Product.Priority.NORMAL);

        // Then
        assertThat(urgentProducts).hasSize(2);
        assertThat(urgentProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Urgent 1", "Urgent 2");
        assertThat(normalProducts).hasSize(1);
        assertThat(normalProducts.get(0).getName()).isEqualTo("Normal");
    }

    @Test
    void findByActiveAndPriority_shouldReturnFilteredProducts() {
        // Given
        Product activeUrgent = createTestProduct("Active Urgent", "https://au.com", true, Product.Priority.URGENT, null);
        Product activeNormal = createTestProduct("Active Normal", "https://an.com", true, Product.Priority.NORMAL, null);
        Product inactiveUrgent = createTestProduct("Inactive Urgent", "https://iu.com", false, Product.Priority.URGENT, null);
        productRepository.saveAll(List.of(activeUrgent, activeNormal, inactiveUrgent));

        // When
        List<Product> activeUrgentProducts = productRepository.findByActiveAndPriority(true, Product.Priority.URGENT);
        List<Product> inactiveUrgentProducts = productRepository.findByActiveAndPriority(false, Product.Priority.URGENT);

        // Then
        assertThat(activeUrgentProducts).hasSize(1);
        assertThat(activeUrgentProducts.get(0).getName()).isEqualTo("Active Urgent");
        assertThat(inactiveUrgentProducts).hasSize(1);
        assertThat(inactiveUrgentProducts.get(0).getName()).isEqualTo("Inactive Urgent");
    }

    @Test
    void findByShopName_shouldReturnProductsByShop() {
        // Given
        Product coupang1 = createTestProductWithShop("Product 1", "https://p1.com", "쿠팡");
        Product coupang2 = createTestProductWithShop("Product 2", "https://p2.com", "쿠팡");
        Product gmarket = createTestProductWithShop("Product 3", "https://p3.com", "G마켓");
        productRepository.saveAll(List.of(coupang1, coupang2, gmarket));

        // When
        List<Product> coupangProducts = productRepository.findByShopName("쿠팡");
        List<Product> gmarketProducts = productRepository.findByShopName("G마켓");

        // Then
        assertThat(coupangProducts).hasSize(2);
        assertThat(coupangProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Product 1", "Product 2");
        assertThat(gmarketProducts).hasSize(1);
        assertThat(gmarketProducts.get(0).getName()).isEqualTo("Product 3");
    }

    @Test
    void findByNameContaining_shouldReturnMatchingProducts() {
        // Given
        Product product1 = createTestProduct("Apple iPhone 14", "https://p1.com", true, Product.Priority.NORMAL, null);
        Product product2 = createTestProduct("Apple iPad Pro", "https://p2.com", true, Product.Priority.NORMAL, null);
        Product product3 = createTestProduct("Samsung Galaxy", "https://p3.com", true, Product.Priority.NORMAL, null);
        productRepository.saveAll(List.of(product1, product2, product3));

        // When
        List<Product> appleProducts = productRepository.findByNameContaining("Apple");
        List<Product> samsungProducts = productRepository.findByNameContaining("Samsung");

        // Then
        assertThat(appleProducts).hasSize(2);
        assertThat(appleProducts).extracting(Product::getName)
                .containsExactlyInAnyOrder("Apple iPhone 14", "Apple iPad Pro");
        assertThat(samsungProducts).hasSize(1);
        assertThat(samsungProducts.get(0).getName()).isEqualTo("Samsung Galaxy");
    }

    @Test
    void existsByUrl_shouldCheckExistence() {
        // Given
        String existingUrl = "https://existing.com/product";
        String nonExistingUrl = "https://nonexisting.com/product";
        Product product = createTestProduct("Product", existingUrl, true, Product.Priority.NORMAL, null);
        productRepository.save(product);

        // When
        boolean exists = productRepository.existsByUrl(existingUrl);
        boolean notExists = productRepository.existsByUrl(nonExistingUrl);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void countByActive_shouldReturnCorrectCount() {
        // Given
        Product active1 = createTestProduct("Active 1", "https://a1.com", true, Product.Priority.NORMAL, null);
        Product active2 = createTestProduct("Active 2", "https://a2.com", true, Product.Priority.NORMAL, null);
        Product inactive = createTestProduct("Inactive", "https://inactive.com", false, Product.Priority.NORMAL, null);
        productRepository.saveAll(List.of(active1, active2, inactive));

        // When
        long activeCount = productRepository.countByActive(true);
        long inactiveCount = productRepository.countByActive(false);

        // Then
        assertThat(activeCount).isEqualTo(2);
        assertThat(inactiveCount).isEqualTo(1);
    }

    @Test
    void findByDiscordUserId_shouldReturnUserProducts() {
        // Given
        String userId1 = "111111111111111111";
        String userId2 = "222222222222222222";

        Product user1Product1 = createTestProduct("User1 P1", "https://u1p1.com", true, Product.Priority.NORMAL, userId1);
        Product user1Product2 = createTestProduct("User1 P2", "https://u1p2.com", true, Product.Priority.NORMAL, userId1);
        Product user2Product = createTestProduct("User2 P", "https://u2p.com", true, Product.Priority.NORMAL, userId2);
        productRepository.saveAll(List.of(user1Product1, user1Product2, user2Product));

        // When
        List<Product> user1Products = productRepository.findByDiscordUserId(userId1);
        List<Product> user2Products = productRepository.findByDiscordUserId(userId2);

        // Then
        assertThat(user1Products).hasSize(2);
        assertThat(user1Products).extracting(Product::getName)
                .containsExactlyInAnyOrder("User1 P1", "User1 P2");
        assertThat(user2Products).hasSize(1);
        assertThat(user2Products.get(0).getName()).isEqualTo("User2 P");
    }

    @Test
    void findByDiscordUserIdAndActive_shouldReturnActiveUserProducts() {
        // Given
        String userId = "333333333333333333";

        Product active = createTestProduct("Active", "https://active.com", true, Product.Priority.NORMAL, userId);
        Product inactive = createTestProduct("Inactive", "https://inactive.com", false, Product.Priority.NORMAL, userId);
        productRepository.saveAll(List.of(active, inactive));

        // When
        List<Product> activeProducts = productRepository.findByDiscordUserIdAndActive(userId, true);
        List<Product> inactiveProducts = productRepository.findByDiscordUserIdAndActive(userId, false);

        // Then
        assertThat(activeProducts).hasSize(1);
        assertThat(activeProducts.get(0).getName()).isEqualTo("Active");
        assertThat(inactiveProducts).hasSize(1);
        assertThat(inactiveProducts.get(0).getName()).isEqualTo("Inactive");
    }

    @Test
    void findByDiscordUserIdAndName_shouldReturnMatchingProduct() {
        // Given
        String userId = "444444444444444444";

        Product product1 = createTestProduct("My Product", "https://myproduct.com", true, Product.Priority.NORMAL, userId);
        Product product2 = createTestProduct("Other Product", "https://other.com", true, Product.Priority.NORMAL, userId);
        productRepository.saveAll(List.of(product1, product2));

        // When
        List<Product> found = productRepository.findByDiscordUserIdAndName(userId, "My Product");

        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("My Product");
        assertThat(found.get(0).getDiscordUserId()).isEqualTo(userId);
    }

    @Test
    void findByDiscordUserIdAndPriority_shouldReturnUserPriorityProducts() {
        // Given
        String userId = "555555555555555555";

        Product urgent = createTestProduct("Urgent", "https://urgent.com", true, Product.Priority.URGENT, userId);
        Product normal = createTestProduct("Normal", "https://normal.com", true, Product.Priority.NORMAL, userId);
        productRepository.saveAll(List.of(urgent, normal));

        // When
        List<Product> urgentProducts = productRepository.findByDiscordUserIdAndPriority(userId, Product.Priority.URGENT);
        List<Product> normalProducts = productRepository.findByDiscordUserIdAndPriority(userId, Product.Priority.NORMAL);

        // Then
        assertThat(urgentProducts).hasSize(1);
        assertThat(urgentProducts.get(0).getName()).isEqualTo("Urgent");
        assertThat(normalProducts).hasSize(1);
        assertThat(normalProducts.get(0).getName()).isEqualTo("Normal");
    }

    @Test
    void findByDiscordUserIdAndActiveAndPriority_shouldReturnFilteredProducts() {
        // Given
        String userId = "666666666666666666";

        Product activeUrgent = createTestProduct("Active Urgent", "https://au.com", true, Product.Priority.URGENT, userId);
        Product activeNormal = createTestProduct("Active Normal", "https://an.com", true, Product.Priority.NORMAL, userId);
        Product inactiveUrgent = createTestProduct("Inactive Urgent", "https://iu.com", false, Product.Priority.URGENT, userId);
        productRepository.saveAll(List.of(activeUrgent, activeNormal, inactiveUrgent));

        // When
        List<Product> activeUrgentProducts = productRepository.findByDiscordUserIdAndActiveAndPriority(userId, true, Product.Priority.URGENT);
        List<Product> inactiveUrgentProducts = productRepository.findByDiscordUserIdAndActiveAndPriority(userId, false, Product.Priority.URGENT);

        // Then
        assertThat(activeUrgentProducts).hasSize(1);
        assertThat(activeUrgentProducts.get(0).getName()).isEqualTo("Active Urgent");
        assertThat(inactiveUrgentProducts).hasSize(1);
        assertThat(inactiveUrgentProducts.get(0).getName()).isEqualTo("Inactive Urgent");
    }

    @Test
    void existsByDiscordUserIdAndUrl_shouldCheckUserProductExistence() {
        // Given
        String userId = "777777777777777777";
        String existingUrl = "https://existing.com/product";
        String nonExistingUrl = "https://nonexisting.com/product";

        Product product = createTestProduct("Product", existingUrl, true, Product.Priority.NORMAL, userId);
        productRepository.save(product);

        // When
        boolean exists = productRepository.existsByDiscordUserIdAndUrl(userId, existingUrl);
        boolean notExists = productRepository.existsByDiscordUserIdAndUrl(userId, nonExistingUrl);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void delete_shouldRemoveProduct() {
        // Given
        Product product = createTestProduct("To Delete", "https://delete.com", true, Product.Priority.NORMAL, null);
        Product saved = productRepository.save(product);

        // When
        productRepository.deleteById(saved.getId());

        // Then
        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void update_shouldModifyProduct() {
        // Given
        Product product = createTestProduct("Original", "https://original.com", true, Product.Priority.NORMAL, null);
        Product saved = productRepository.save(product);

        // When
        saved.setName("Updated");
        saved.setActive(false);
        saved.setPriority(Product.Priority.URGENT);
        Product updated = productRepository.save(saved);

        // Then
        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getPriority()).isEqualTo(Product.Priority.URGENT);
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
    }

    @Test
    void save_withPriceAndStatus_shouldPersist() {
        // Given
        Product product = Product.builder()
                .name("Product with Price")
                .url("https://withprice.com/product")
                .active(true)
                .priority(Product.Priority.NORMAL)
                .notifyOnRestock(true)
                .currentStatus(Product.StockStatus.IN_STOCK)
                .currentPrice(new BigDecimal("29990.00"))
                .checkIntervalMinutes(3)
                .consecutiveFailures(0)
                .build();

        // When
        Product saved = productRepository.save(product);

        // Then
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("29990.00"));
        assertThat(saved.getCurrentStatus()).isEqualTo(Product.StockStatus.IN_STOCK);
        assertThat(saved.getNotifyOnRestock()).isTrue();
    }

    /**
     * 테스트용 Product 생성 헬퍼 메서드
     */
    private Product createTestProduct(String name, String url, Boolean active, Product.Priority priority, String discordUserId) {
        return Product.builder()
                .name(name)
                .url(url)
                .active(active)
                .priority(priority)
                .notifyOnRestock(true)
                .currentStatus(Product.StockStatus.UNKNOWN)
                .checkIntervalMinutes(3)
                .consecutiveFailures(0)
                .discordUserId(discordUserId)
                .build();
    }

    /**
     * 쇼핑몰 이름을 포함한 테스트용 Product 생성 헬퍼 메서드
     */
    private Product createTestProductWithShop(String name, String url, String shopName) {
        return Product.builder()
                .name(name)
                .url(url)
                .shopName(shopName)
                .active(true)
                .priority(Product.Priority.NORMAL)
                .notifyOnRestock(true)
                .currentStatus(Product.StockStatus.UNKNOWN)
                .checkIntervalMinutes(3)
                .consecutiveFailures(0)
                .build();
    }
}
