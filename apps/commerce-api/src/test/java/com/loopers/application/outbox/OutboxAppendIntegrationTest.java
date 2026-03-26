package com.loopers.application.outbox;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class OutboxAppendIntegrationTest {

    @Autowired
    private com.loopers.application.like.LikeFacade likeFacade;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveProduct() {
        var brand = brandService.registerBrand("테스트 브랜드");
        ProductModel product = productService.registerProduct(brand.getId(), "테스트 상품", new BigDecimal("10000"), 10);
        return product.getId();
    }

    @Test
    @DisplayName("좋아요 추가가 성공하면 doAddLike 트랜잭션에 Outbox 이벤트가 함께 적재된다.")
    void addLike_whenSuccess_shouldAppendOutboxInSameTransaction() {
        Long productId = saveProduct();

        likeFacade.addLike(1L, productId);

        assertThat(outboxJpaRepository.count()).isEqualTo(1);
        var saved = outboxJpaRepository.findAll().get(0);
        assertThat(saved.getTopic()).isEqualTo("product-events");
        assertThat(saved.getPartitionKey()).isEqualTo(String.valueOf(productId));
        assertThat(saved.getEventType()).isEqualTo("PRODUCT_LIKE_CHANGED");
        assertThat(saved.isPublished()).isFalse();
    }
}

