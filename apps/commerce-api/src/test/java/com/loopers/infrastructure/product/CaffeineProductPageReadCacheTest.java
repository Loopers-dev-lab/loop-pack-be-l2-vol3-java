package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.ProductSortType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineProductPageReadCacheTest {

    private CaffeineProductPageReadCache pageReadCache;

    @BeforeEach
    void setUp() {
        pageReadCache = new CaffeineProductPageReadCache(100, 5);
    }

    private PageResult<ProductReadModel> createPage(int itemCount) {
        ZonedDateTime now = ZonedDateTime.now();
        List<ProductReadModel> items = java.util.stream.IntStream.range(0, itemCount)
            .mapToObj(i -> new ProductReadModel((long) i, 1L, "상품" + i, 10000 * (i + 1), 0, now, now))
            .toList();
        return new PageResult<>(items, 0, 20, itemCount, 1);
    }

    @DisplayName("get을 호출할 때, ")
    @Nested
    class Get {

        @DisplayName("캐시 미스이면, loader를 호출하여 값을 저장하고 반환한다.")
        @Test
        void callsLoader_whenCacheMiss() {
            // given
            AtomicInteger loaderCallCount = new AtomicInteger(0);
            PageResult<ProductReadModel> expected = createPage(2);

            // when
            PageResult<ProductReadModel> result = pageReadCache.get(
                ProductSortType.LATEST, 0, 20, () -> {
                    loaderCallCount.incrementAndGet();
                    return expected;
                }
            );

            // then
            assertThat(result.items()).hasSize(2);
            assertThat(loaderCallCount.get()).isEqualTo(1);
        }

        @DisplayName("캐시 히트이면, loader를 호출하지 않고 캐시된 값을 반환한다.")
        @Test
        void skipsLoader_whenCacheHit() {
            // given
            PageResult<ProductReadModel> expected = createPage(2);
            pageReadCache.get(ProductSortType.LATEST, 0, 20, () -> expected); // 캐시 채움

            AtomicInteger loaderCallCount = new AtomicInteger(0);

            // when
            PageResult<ProductReadModel> result = pageReadCache.get(
                ProductSortType.LATEST, 0, 20, () -> {
                    loaderCallCount.incrementAndGet();
                    return createPage(5);
                }
            );

            // then
            assertThat(result.items()).hasSize(2);
            assertThat(loaderCallCount.get()).isEqualTo(0);
        }

        @DisplayName("정렬 기준이 다르면, 별도 캐시 엔트리로 관리된다.")
        @Test
        void separatesCacheBySort() {
            // given
            PageResult<ProductReadModel> latestPage = createPage(2);
            PageResult<ProductReadModel> pricePage = createPage(3);
            pageReadCache.get(ProductSortType.LATEST, 0, 20, () -> latestPage);
            pageReadCache.get(ProductSortType.PRICE_ASC, 0, 20, () -> pricePage);

            // when & then
            AtomicInteger loaderCallCount = new AtomicInteger(0);
            PageResult<ProductReadModel> result = pageReadCache.get(
                ProductSortType.LATEST, 0, 20, () -> {
                    loaderCallCount.incrementAndGet();
                    return createPage(10);
                }
            );
            assertThat(result.items()).hasSize(2); // 캐시된 latestPage 반환
            assertThat(loaderCallCount.get()).isEqualTo(0);
        }
    }

    @DisplayName("evictAll을 호출할 때, ")
    @Nested
    class EvictAll {

        @DisplayName("모든 캐시가 제거되어 loader가 다시 호출된다.")
        @Test
        void removesAllEntries() {
            // given
            pageReadCache.get(ProductSortType.LATEST, 0, 20, () -> createPage(2));
            pageReadCache.get(ProductSortType.PRICE_ASC, 0, 20, () -> createPage(3));

            // when
            pageReadCache.evictAll();

            // then
            AtomicInteger loaderCallCount = new AtomicInteger(0);
            pageReadCache.get(ProductSortType.LATEST, 0, 20, () -> {
                loaderCallCount.incrementAndGet();
                return createPage(5);
            });
            assertThat(loaderCallCount.get()).isEqualTo(1);
        }
    }
}
