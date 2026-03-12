package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.shared.cache.CacheRepository;
import com.loopers.domain.shared.cache.CacheType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

@ExtendWith(MockitoExtension.class)
class ProductReaderTest {

    @InjectMocks
    private ProductReader productReader;

    @Mock
    private CacheRepository cacheRepository;

    @Mock
    private ProductService productService;

    @Captor
    private ArgumentCaptor<Map<String, Product>> multiPutCaptor;

    @DisplayName("활성 상품 목록을 조회할 때,")
    @Nested
    class ReadActiveProducts {

        @DisplayName("목록 캐시 HIT + 상세 캐시 전부 HIT이면, DB를 호출하지 않는다.")
        @Test
        void returnsFromCache_whenListAndDetailAllHit() {
            // arrange
            var pageSize = new PageSize(0, 20);
            var product1 = ProductFixture.createProduct(1L);
            var product2 = ProductFixture.createProduct(2L);
            var idPage = new ProductReader.ProductIdPage(List.of(1L, 2L), true);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(idPage);
            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(List.of(product1, product2));

            // act
            Page<Product> result = productReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).containsExactly(product1, product2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
            then(productService).shouldHaveNoInteractions();
        }

        @DisplayName("목록 캐시 HIT + 상세 캐시 부분 MISS이면, 미스된 상품만 DB에서 조회하고 multiPut으로 캐싱한다.")
        @Test
        void fetchesMissingFromDb_whenPartialDetailMiss() {
            // arrange
            var pageSize = new PageSize(0, 20);
            var product1 = ProductFixture.createProduct(1L);
            var product3 = ProductFixture.createProduct(3L);
            var idPage = new ProductReader.ProductIdPage(List.of(1L, 2L, 3L), false);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(idPage);
            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(Arrays.asList(product1, null, product3));

            var product2 = ProductFixture.createProduct(2L);
            given(productService.getActiveProductsByIds(List.of(2L)))
                    .willReturn(Map.of(2L, product2));

            // act
            var result = productReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(3),
                    () -> assertThat(result.content().get(0).getId()).isEqualTo(1L),
                    () -> assertThat(result.content().get(1).getId()).isEqualTo(2L),
                    () -> assertThat(result.content().get(2).getId()).isEqualTo(3L),
                    () -> assertThat(result.hasNext()).isFalse()
            );
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), eq(ProductCacheConstants.DETAIL_TTL));
            assertThat(multiPutCaptor.getValue()).containsKey(ProductCacheConstants.DETAIL_KEY.of(2L));
        }

        @DisplayName("목록 캐시 MISS이면, DB에서 조회하고 양쪽 캐시에 저장한다.")
        @Test
        void fetchesFromDbAndCachesBoth_whenListCacheMiss() {
            // arrange
            var pageSize = new PageSize(0, 20);
            var product1 = ProductFixture.createProduct(1L);
            var product2 = ProductFixture.createProduct(2L);
            var dbPage = new Page<>(List.of(product1, product2), true);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(null);
            given(productService.getActiveProducts(null, ProductSortType.DEFAULT, pageSize)).willReturn(dbPage);

            // act
            var result = productReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).containsExactly(product1, product2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
            then(cacheRepository).should().put(anyString(), any(ProductReader.ProductIdPage.class), any());
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), eq(ProductCacheConstants.DETAIL_TTL));
            Map<String, Product> cached = multiPutCaptor.getValue();
            assertAll(
                    () -> assertThat(cached).containsKey(ProductCacheConstants.DETAIL_KEY.of(1L)),
                    () -> assertThat(cached).containsKey(ProductCacheConstants.DETAIL_KEY.of(2L))
            );
        }

        @DisplayName("캐시 불가 페이지(page > 2)이면, 목록 캐시를 저장하지 않지만 상세 캐시는 저장한다.")
        @Test
        void doesNotCacheList_whenPageExceedsMax() {
            // arrange
            var pageSize = new PageSize(3, 20);
            var product1 = ProductFixture.createProduct(1L);
            var dbPage = new Page<>(List.of(product1), false);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(null);
            given(productService.getActiveProducts(null, ProductSortType.DEFAULT, pageSize)).willReturn(dbPage);

            // act
            productReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            then(cacheRepository).should(never()).put(anyString(), any(ProductReader.ProductIdPage.class), any());
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), eq(ProductCacheConstants.DETAIL_TTL));
            assertThat(multiPutCaptor.getValue()).containsKey(ProductCacheConstants.DETAIL_KEY.of(1L));
        }
    }
}
