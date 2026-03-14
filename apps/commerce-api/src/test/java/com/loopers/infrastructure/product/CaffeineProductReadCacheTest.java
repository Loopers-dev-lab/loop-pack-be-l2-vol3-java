package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.application.product.ProductReadModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineProductReadCacheTest {

    private Cache<Long, ProductReadModel> caffeineCache;
    private CaffeineProductReadCache productReadCache;

    @BeforeEach
    void setUp() {
        caffeineCache = Caffeine.newBuilder().build();
        productReadCache = new CaffeineProductReadCache(caffeineCache);
    }

    private ProductReadModel createReadModel(Long id, String name) {
        ZonedDateTime now = ZonedDateTime.now();
        return new ProductReadModel(id, 1L, name, 129000, 0, now, now);
    }

    @DisplayName("get을 호출할 때, ")
    @Nested
    class Get {

        @DisplayName("캐시 미스이면, loader를 호출하여 값을 저장하고 반환한다.")
        @Test
        void callsLoader_whenCacheMiss() {
            // given
            AtomicInteger loaderCallCount = new AtomicInteger(0);
            ProductReadModel expected = createReadModel(1L, "에어맥스");

            // when
            ProductReadModel result = productReadCache.get(1L, () -> {
                loaderCallCount.incrementAndGet();
                return expected;
            });

            // then
            assertThat(result).isEqualTo(expected);
            assertThat(loaderCallCount.get()).isEqualTo(1);
            assertThat(caffeineCache.getIfPresent(1L)).isEqualTo(expected);
        }

        @DisplayName("캐시 히트이면, loader를 호출하지 않고 캐시된 값을 반환한다.")
        @Test
        void skipsLoader_whenCacheHit() {
            // given
            ProductReadModel expected = createReadModel(1L, "에어맥스");
            caffeineCache.put(1L, expected);
            AtomicInteger loaderCallCount = new AtomicInteger(0);

            // when
            ProductReadModel result = productReadCache.get(1L, () -> {
                loaderCallCount.incrementAndGet();
                return createReadModel(1L, "다른상품");
            });

            // then
            assertThat(result).isEqualTo(expected);
            assertThat(loaderCallCount.get()).isEqualTo(0);
        }

        @DisplayName("loader가 null을 반환하면, 캐시에 저장하지 않고 null을 반환한다.")
        @Test
        void returnsNull_whenLoaderReturnsNull() {
            // when
            ProductReadModel result = productReadCache.get(1L, () -> null);

            // then
            assertThat(result).isNull();
            assertThat(caffeineCache.getIfPresent(1L)).isNull();
        }
    }

    @DisplayName("evict를 호출할 때, ")
    @Nested
    class Evict {

        @DisplayName("해당 키의 캐시가 제거되어 재조회 시 loader가 다시 호출된다.")
        @Test
        void removesEntry_andLoaderIsCalledAgain() {
            // given
            ProductReadModel original = createReadModel(1L, "에어맥스");
            caffeineCache.put(1L, original);

            // when
            productReadCache.evict(1L);

            // then
            assertThat(caffeineCache.getIfPresent(1L)).isNull();

            AtomicInteger loaderCallCount = new AtomicInteger(0);
            ProductReadModel updated = createReadModel(1L, "에어포스1");
            productReadCache.get(1L, () -> {
                loaderCallCount.incrementAndGet();
                return updated;
            });
            assertThat(loaderCallCount.get()).isEqualTo(1);
        }
    }

    @DisplayName("evictAll을 호출할 때, ")
    @Nested
    class EvictAll {

        @DisplayName("모든 캐시가 제거된다.")
        @Test
        void removesAllEntries() {
            // given
            caffeineCache.put(1L, createReadModel(1L, "에어맥스"));
            caffeineCache.put(2L, createReadModel(2L, "에어포스1"));

            // when
            productReadCache.evictAll();

            // then
            assertThat(caffeineCache.getIfPresent(1L)).isNull();
            assertThat(caffeineCache.getIfPresent(2L)).isNull();
        }
    }
}
