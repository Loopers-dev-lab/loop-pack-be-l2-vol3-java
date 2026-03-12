package com.loopers.infrastructure.product;

import com.loopers.interfaces.api.product.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineProductCacheAdapterTest {

    private CaffeineProductCacheAdapter cache;

    @BeforeEach
    void setUp() {
        cache = new CaffeineProductCacheAdapter();
    }

    @Nested
    @DisplayName("상품 상세 캐시")
    class DetailCache {

        @DisplayName("put 후 get하면 저장된 값이 반환된다")
        @Test
        void putAndGet() {
            ProductDto.ProductResponse response = new ProductDto.ProductResponse(
                1L, 10L, "나이키", "에어맥스", 150000, 10, 5);

            cache.putProductDetail(1L, response);

            ProductDto.ProductResponse cached = cache.getProductDetail(1L);
            assertThat(cached).isEqualTo(response);
        }

        @DisplayName("캐시에 없는 상품은 null을 반환한다")
        @Test
        void getReturnsNullOnMiss() {
            assertThat(cache.getProductDetail(999L)).isNull();
        }

        @DisplayName("evict 후 get하면 null을 반환한다")
        @Test
        void evictRemovesEntry() {
            ProductDto.ProductResponse response = new ProductDto.ProductResponse(
                1L, 10L, "나이키", "에어맥스", 150000, 10, 5);
            cache.putProductDetail(1L, response);

            cache.evictProductDetail(1L);

            assertThat(cache.getProductDetail(1L)).isNull();
        }
    }

    @Nested
    @DisplayName("상품 목록 캐시")
    class ListCache {

        @DisplayName("put 후 get하면 저장된 값이 반환된다")
        @Test
        void putAndGet() {
            ProductDto.PagedProductResponse response = new ProductDto.PagedProductResponse(
                List.of(), 0, 0, 0, 20);

            cache.putProductList(null, "latest", 0, 20, response);

            ProductDto.PagedProductResponse cached = cache.getProductList(null, "latest", 0, 20);
            assertThat(cached).isEqualTo(response);
        }

        @DisplayName("brandId가 다르면 별도 캐시 엔트리이다")
        @Test
        void differentBrandIdIsSeparateEntry() {
            ProductDto.PagedProductResponse allBrands = new ProductDto.PagedProductResponse(
                List.of(), 100, 5, 0, 20);
            ProductDto.PagedProductResponse brand1 = new ProductDto.PagedProductResponse(
                List.of(), 10, 1, 0, 20);

            cache.putProductList(null, "latest", 0, 20, allBrands);
            cache.putProductList(1L, "latest", 0, 20, brand1);

            assertThat(cache.getProductList(null, "latest", 0, 20).totalElements()).isEqualTo(100);
            assertThat(cache.getProductList(1L, "latest", 0, 20).totalElements()).isEqualTo(10);
        }

        @DisplayName("evictProductList는 모든 목록 캐시를 무효화한다")
        @Test
        void evictClearsAllListEntries() {
            cache.putProductList(null, "latest", 0, 20, new ProductDto.PagedProductResponse(
                List.of(), 0, 0, 0, 20));
            cache.putProductList(1L, "likes_desc", 0, 10, new ProductDto.PagedProductResponse(
                List.of(), 0, 0, 0, 10));

            cache.evictProductList();

            assertThat(cache.getProductList(null, "latest", 0, 20)).isNull();
            assertThat(cache.getProductList(1L, "likes_desc", 0, 10)).isNull();
        }
    }
}
