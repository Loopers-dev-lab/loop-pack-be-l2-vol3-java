package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.infrastructure.product.ProductCacheManager.CachedPage;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductCacheManagerIntegrationTest {

    @Autowired
    private ProductCacheManager productCacheManager;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @Nested
    class 상품_상세_캐시 {

        @Test
        void 캐시_저장_후_조회하면_동일한_데이터를_반환한다() {
            // given
            Long productId = 1L;
            ProductInfo info = createProductInfo(productId);

            // when
            productCacheManager.putDetail(productId, info);
            Optional<ProductInfo> cached = productCacheManager.getDetail(productId);

            // then
            assertThat(cached).isPresent();
            ProductInfo result = cached.get();
            assertThat(result.id()).isEqualTo(productId);
            assertThat(result.name()).isEqualTo("테스트 상품");
            assertThat(result.price()).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(result.brandName()).isEqualTo("테스트 브랜드");
        }

        @Test
        void 캐시가_없으면_빈_Optional을_반환한다() {
            // when
            Optional<ProductInfo> cached = productCacheManager.getDetail(999L);

            // then
            assertThat(cached).isEmpty();
        }

        @Test
        void 캐시를_삭제하면_조회되지_않는다() {
            // given
            Long productId = 1L;
            productCacheManager.putDetail(productId, createProductInfo(productId));

            // when
            productCacheManager.evictDetail(productId);
            Optional<ProductInfo> cached = productCacheManager.getDetail(productId);

            // then
            assertThat(cached).isEmpty();
        }
    }

    @Nested
    class 상품_목록_캐시 {

        @Test
        void 캐시_저장_후_조회하면_동일한_데이터를_반환한다() {
            // given
            Long brandId = 1L;
            String sort = "createdAt: DESC";
            int page = 0;
            int size = 20;
            CachedPage cachedPage = new CachedPage(
                    List.of(createProductInfo(1L), createProductInfo(2L)),
                    page, size, 2L
            );

            // when
            productCacheManager.putList(brandId, sort, page, size, cachedPage);
            Optional<CachedPage> cached = productCacheManager.getList(brandId, sort, page, size);

            // then
            assertThat(cached).isPresent();
            CachedPage result = cached.get();
            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @Test
        void 캐시가_없으면_빈_Optional을_반환한다() {
            // when
            Optional<CachedPage> cached = productCacheManager.getList(1L, "createdAt: DESC", 0, 20);

            // then
            assertThat(cached).isEmpty();
        }

        @Test
        void brandId가_null이면_all로_캐시된다() {
            // given
            CachedPage cachedPage = new CachedPage(
                    List.of(createProductInfo(1L)),
                    0, 20, 1L
            );

            // when
            productCacheManager.putList(null, "createdAt: DESC", 0, 20, cachedPage);
            Optional<CachedPage> cached = productCacheManager.getList(null, "createdAt: DESC", 0, 20);

            // then
            assertThat(cached).isPresent();
        }

        @Test
        void 목록_캐시를_전체_삭제하면_조회되지_않는다() {
            // given
            productCacheManager.putList(1L, "createdAt: DESC", 0, 20,
                    new CachedPage(List.of(createProductInfo(1L)), 0, 20, 1L));
            productCacheManager.putList(2L, "price: ASC", 0, 20,
                    new CachedPage(List.of(createProductInfo(2L)), 0, 20, 1L));

            // when
            productCacheManager.evictAllLists();

            // then
            assertThat(productCacheManager.getList(1L, "createdAt: DESC", 0, 20)).isEmpty();
            assertThat(productCacheManager.getList(2L, "price: ASC", 0, 20)).isEmpty();
        }
    }

    private ProductInfo createProductInfo(Long id) {
        return new ProductInfo(
                id, 1L, "테스트 브랜드", "테스트 상품",
                new BigDecimal("10000"), 100, "설명",
                0, ProductInfo.Status.ACTIVE,
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 1, 1, 0, 0),
                null
        );
    }
}
