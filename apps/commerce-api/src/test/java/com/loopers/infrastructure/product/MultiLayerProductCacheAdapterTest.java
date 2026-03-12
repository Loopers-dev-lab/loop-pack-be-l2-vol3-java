package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductCachePort;
import com.loopers.interfaces.api.product.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiLayerProductCacheAdapterTest {

    private SpyProductCachePort l1;
    private SpyProductCachePort l2;
    private MultiLayerProductCacheAdapter multiLayer;

    @BeforeEach
    void setUp() {
        l1 = new SpyProductCachePort();
        l2 = new SpyProductCachePort();
        multiLayer = new MultiLayerProductCacheAdapter(l1, l2);
    }

    @Nested
    @DisplayName("상품 상세 GET")
    class GetDetail {

        @DisplayName("L1 히트 시 L1에서 반환하고 L2를 조회하지 않는다")
        @Test
        void l1Hit_returnsFromL1() {
            ProductDto.ProductResponse response = detailResponse(1L);
            l1.putProductDetail(1L, response);

            ProductDto.ProductResponse result = multiLayer.getProductDetail(1L);

            assertThat(result).isEqualTo(response);
            assertThat(l2.getCallCount).isZero();
        }

        @DisplayName("L1 미스 + L2 히트 시 L2에서 반환하고 L1에 backfill한다")
        @Test
        void l1Miss_l2Hit_backfillsL1() {
            ProductDto.ProductResponse response = detailResponse(1L);
            l2.putProductDetail(1L, response);

            ProductDto.ProductResponse result = multiLayer.getProductDetail(1L);

            assertThat(result).isEqualTo(response);
            assertThat(l1.getProductDetail(1L)).isEqualTo(response);
        }

        @DisplayName("L1 미스 + L2 미스 시 null을 반환한다")
        @Test
        void bothMiss_returnsNull() {
            assertThat(multiLayer.getProductDetail(999L)).isNull();
        }
    }

    @Nested
    @DisplayName("상품 상세 PUT")
    class PutDetail {

        @DisplayName("L2 먼저, L1에도 저장한다")
        @Test
        void putStoresInBothLayers() {
            ProductDto.ProductResponse response = detailResponse(1L);

            multiLayer.putProductDetail(1L, response);

            assertThat(l2.getProductDetail(1L)).isEqualTo(response);
            assertThat(l1.getProductDetail(1L)).isEqualTo(response);
        }
    }

    @Nested
    @DisplayName("상품 상세 EVICT")
    class EvictDetail {

        @DisplayName("L1과 L2 모두에서 삭제한다")
        @Test
        void evictRemovesFromBothLayers() {
            ProductDto.ProductResponse response = detailResponse(1L);
            l1.putProductDetail(1L, response);
            l2.putProductDetail(1L, response);

            multiLayer.evictProductDetail(1L);

            assertThat(l1.getProductDetail(1L)).isNull();
            assertThat(l2.getProductDetail(1L)).isNull();
        }
    }

    @Nested
    @DisplayName("상품 목록 GET")
    class GetList {

        @DisplayName("L1 히트 시 L1에서 반환한다")
        @Test
        void l1Hit_returnsFromL1() {
            ProductDto.PagedProductResponse response = listResponse();
            l1.putProductList(null, "latest", 0, 20, response);

            ProductDto.PagedProductResponse result = multiLayer.getProductList(null, "latest", 0, 20);

            assertThat(result).isEqualTo(response);
        }

        @DisplayName("L1 미스 + L2 히트 시 L1에 backfill한다")
        @Test
        void l1Miss_l2Hit_backfillsL1() {
            ProductDto.PagedProductResponse response = listResponse();
            l2.putProductList(null, "latest", 0, 20, response);

            multiLayer.getProductList(null, "latest", 0, 20);

            assertThat(l1.getProductList(null, "latest", 0, 20)).isEqualTo(response);
        }
    }

    @Nested
    @DisplayName("상품 목록 EVICT")
    class EvictList {

        @DisplayName("L1과 L2 모두에서 목록을 무효화한다")
        @Test
        void evictClearsBothLayers() {
            l1.putProductList(null, "latest", 0, 20, listResponse());
            l2.putProductList(null, "latest", 0, 20, listResponse());

            multiLayer.evictProductList();

            assertThat(l1.getProductList(null, "latest", 0, 20)).isNull();
            assertThat(l2.getProductList(null, "latest", 0, 20)).isNull();
        }
    }

    // ── 헬퍼 ──

    private static ProductDto.ProductResponse detailResponse(Long id) {
        return new ProductDto.ProductResponse(id, 10L, "나이키", "에어맥스", 150000, 10, 5);
    }

    private static ProductDto.PagedProductResponse listResponse() {
        return new ProductDto.PagedProductResponse(List.of(), 0, 0, 0, 20);
    }

    /**
     * 테스트용 Spy — HashMap 기반 캐시 + 호출 횟수 카운팅
     */
    static class SpyProductCachePort implements ProductCachePort {

        private final Map<Long, ProductDto.ProductResponse> detailStore = new HashMap<>();
        private final Map<String, ProductDto.PagedProductResponse> listStore = new HashMap<>();
        int getCallCount = 0;

        @Override
        public ProductDto.ProductResponse getProductDetail(Long productId) {
            getCallCount++;
            return detailStore.get(productId);
        }

        @Override
        public void putProductDetail(Long productId, ProductDto.ProductResponse response) {
            detailStore.put(productId, response);
        }

        @Override
        public void evictProductDetail(Long productId) {
            detailStore.remove(productId);
        }

        @Override
        public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) {
            return listStore.get(listKey(brandId, sort, page, size));
        }

        @Override
        public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {
            listStore.put(listKey(brandId, sort, page, size), response);
        }

        @Override
        public void evictProductList() {
            listStore.clear();
        }

        private String listKey(Long brandId, String sort, int page, int size) {
            String brandPart = brandId != null ? String.valueOf(brandId) : "all";
            return brandPart + ":" + sort + ":" + page + ":" + size;
        }
    }
}
