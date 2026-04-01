package com.loopers.interfaces.listener;

import com.loopers.application.product.ProductCachePort;
import com.loopers.domain.event.LikeCreatedEvent;
import com.loopers.domain.event.LikeRemovedEvent;
import com.loopers.interfaces.api.product.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheEvictionEventListenerTest {

    private CacheEvictionEventListener listener;
    private SpyProductCachePort cachePort;

    @BeforeEach
    void setUp() {
        cachePort = new SpyProductCachePort();
        listener = new CacheEvictionEventListener(cachePort);
    }

    @Nested
    @DisplayName("LikeCreatedEvent 처리")
    class HandleLikeCreated {

        @DisplayName("상품 상세 캐시와 목록 캐시가 무효화된다")
        @Test
        void evictsProductDetailAndList() {
            listener.handleLikeCreated(new LikeCreatedEvent(100L, 1L));

            assertThat(cachePort.evictedProductDetailIds).containsExactly(100L);
            assertThat(cachePort.evictProductListCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("LikeRemovedEvent 처리")
    class HandleLikeRemoved {

        @DisplayName("상품 상세 캐시와 목록 캐시가 무효화된다")
        @Test
        void evictsProductDetailAndList() {
            listener.handleLikeRemoved(new LikeRemovedEvent(200L, 1L));

            assertThat(cachePort.evictedProductDetailIds).containsExactly(200L);
            assertThat(cachePort.evictProductListCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("캐시 무효화 실패")
    class CacheEvictionFailure {

        @DisplayName("캐시 무효화 중 예외가 발생해도 best-effort로 처리된다")
        @Test
        void doesNotPropagateException() {
            CacheEvictionEventListener failingListener = new CacheEvictionEventListener(
                new FailingProductCachePort());

            failingListener.handleLikeCreated(new LikeCreatedEvent(100L, 1L));
            failingListener.handleLikeRemoved(new LikeRemovedEvent(200L, 1L));
        }
    }

    static class SpyProductCachePort implements ProductCachePort {
        final List<Long> evictedProductDetailIds = new ArrayList<>();
        int evictProductListCount = 0;

        @Override public ProductDto.ProductResponse getProductDetail(Long productId) { return null; }
        @Override public void putProductDetail(Long productId, ProductDto.ProductResponse response) {}
        @Override public void evictProductDetail(Long productId) { evictedProductDetailIds.add(productId); }
        @Override public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) { return null; }
        @Override public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {}
        @Override public void evictProductList() { evictProductListCount++; }
    }

    static class FailingProductCachePort implements ProductCachePort {
        @Override public ProductDto.ProductResponse getProductDetail(Long productId) { return null; }
        @Override public void putProductDetail(Long productId, ProductDto.ProductResponse response) {}
        @Override public void evictProductDetail(Long productId) { throw new RuntimeException("Redis down"); }
        @Override public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) { return null; }
        @Override public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {}
        @Override public void evictProductList() { throw new RuntimeException("Redis down"); }
    }
}
