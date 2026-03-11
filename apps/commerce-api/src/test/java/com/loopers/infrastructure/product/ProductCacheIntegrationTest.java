package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.loopers.application.product.ProductQueryService;
import com.loopers.application.product.ProductReadCache;
import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductReadCache productReadCache;

    @Autowired
    private Cache<Long, ProductReadModel> productDetailCache;

    private Long brandId;

    @BeforeEach
    void setUp() {
        productReadCache.evictAll();
        Brand brand = brandService.register("나이키");
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        productReadCache.evictAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("afterCommit 캐시 무효화를 검증할 때, ")
    @Nested
    class AfterCommitEviction {

        @DisplayName("상품 수정 커밋 후, 캐시가 무효화되어 최신 값을 반환한다.")
        @Test
        void evictsCache_afterProductUpdate() {
            // given
            Product product = productService.register(brandId, "에어맥스", 129000);
            ProductReadModel cached = productQueryService.getById(product.getId());
            assertThat(cached.name()).isEqualTo("에어맥스");

            // when
            transactionTemplate.executeWithoutResult(status ->
                productService.update(product.getId(), "에어포스1", 109000)
            );

            // then
            ProductReadModel result = productQueryService.getById(product.getId());
            assertThat(result.name()).isEqualTo("에어포스1");
            assertThat(result.price()).isEqualTo(109000);
        }

        @DisplayName("좋아요 증가 커밋 후, 상세 조회에서 likeCount가 최신값으로 반영된다.")
        @Test
        void evictsCache_afterLikeIncrement() {
            // given
            Product product = productService.register(brandId, "에어맥스", 129000);
            ProductReadModel cached = productQueryService.getById(product.getId());
            assertThat(cached.likeCount()).isEqualTo(0);

            // when
            transactionTemplate.executeWithoutResult(status ->
                productService.incrementLikeCount(product.getId())
            );

            // then
            ProductReadModel result = productQueryService.getById(product.getId());
            assertThat(result.likeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 감소 커밋 후, 상세 조회에서 likeCount가 최신값으로 반영된다.")
        @Test
        void evictsCache_afterLikeDecrement() {
            // given
            Product product = productService.register(brandId, "에어맥스", 129000);
            transactionTemplate.executeWithoutResult(status ->
                productService.incrementLikeCount(product.getId())
            );
            ProductReadModel cached = productQueryService.getById(product.getId());
            assertThat(cached.likeCount()).isEqualTo(1);

            // when
            transactionTemplate.executeWithoutResult(status ->
                productService.decrementLikeCount(product.getId())
            );

            // then
            ProductReadModel result = productQueryService.getById(product.getId());
            assertThat(result.likeCount()).isEqualTo(0);
        }

        @DisplayName("상품 삭제 커밋 후, 캐시가 무효화된다.")
        @Test
        void evictsCache_afterProductDelete() {
            // given
            Product product = productService.register(brandId, "에어맥스", 129000);
            productQueryService.getById(product.getId());

            // when
            transactionTemplate.executeWithoutResult(status ->
                productService.delete(product.getId())
            );

            // then
            assertThat(productDetailCache.getIfPresent(product.getId())).isNull();
        }
    }
}
