package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.loopers.application.product.ProductPageReadCache;
import com.loopers.application.product.ProductQueryService;
import com.loopers.application.product.ProductReadCache;
import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductSortType;
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
    private ProductPageReadCache productPageReadCache;

    @Autowired
    private Cache<Long, ProductReadModel> productDetailCache;

    private Long brandId;

    @BeforeEach
    void setUp() {
        productReadCache.evictAll();
        productPageReadCache.evictAll();
        Brand brand = brandService.register("나이키");
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        productReadCache.evictAll();
        productPageReadCache.evictAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상세 캐시 afterCommit 무효화를 검증할 때, ")
    @Nested
    class DetailCacheEviction {

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

        @DisplayName("브랜드 삭제 커밋 후, 해당 브랜드 상품의 상세 캐시가 무효화된다.")
        @Test
        void evictsAllDetailCache_afterBrandDelete() {
            // given
            Product product1 = productService.register(brandId, "에어맥스", 129000);
            Product product2 = productService.register(brandId, "에어포스1", 109000);
            productQueryService.getById(product1.getId());
            productQueryService.getById(product2.getId());

            // when
            transactionTemplate.executeWithoutResult(status -> {
                brandService.deleteWithLock(brandId);
                productService.deleteAllByBrandId(brandId);
            });

            // then
            assertThat(productDetailCache.getIfPresent(product1.getId())).isNull();
            assertThat(productDetailCache.getIfPresent(product2.getId())).isNull();
        }
    }

    @DisplayName("페이지 캐시를 검증할 때, ")
    @Nested
    class PageCacheEviction {

        @DisplayName("전체 목록 조회 시 캐시가 적용되고, 상품 변경 커밋 후 무효화된다.")
        @Test
        void evictsPageCache_afterProductUpdate() {
            // given
            productService.register(brandId, "에어맥스", 129000);
            productService.register(brandId, "에어포스1", 109000);

            PageResult<ProductReadModel> cached = productQueryService.getAll(
                null, ProductSortType.LATEST, 0, 20
            );
            assertThat(cached.items()).hasSize(2);

            // when — 새 상품 등록 후 커밋
            productService.register(brandId, "뉴발란스 990", 199000);

            // then — 페이지 캐시 무효화되어 3개 반환
            PageResult<ProductReadModel> result = productQueryService.getAll(
                null, ProductSortType.LATEST, 0, 20
            );
            assertThat(result.items()).hasSize(3);
        }

        @DisplayName("브랜드 필터 조회는 캐시하지 않는다.")
        @Test
        void doesNotCacheBrandFilteredQueries() {
            // given
            productService.register(brandId, "에어맥스", 129000);

            productQueryService.getAll(brandId, ProductSortType.LATEST, 0, 20);

            // when — 새 상품 등록 (캐시 무효화 없이도 최신값이어야 함)
            productService.register(brandId, "에어포스1", 109000);

            // then — 캐시가 아닌 DB 직접 조회이므로 2개 반환
            PageResult<ProductReadModel> result = productQueryService.getAll(
                brandId, ProductSortType.LATEST, 0, 20
            );
            assertThat(result.items()).hasSize(2);
        }
    }
}
