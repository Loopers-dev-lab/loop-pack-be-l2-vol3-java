package com.loopers.application.product.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.cache.CacheRepository;
import com.loopers.support.cache.CacheType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

@ExtendWith(MockitoExtension.class)
class ProductCacheReaderTest {

    private ProductCacheReader productCacheReader;

    @Mock
    private CacheRepository cacheRepository;

    @Mock
    private ProductService productService;

    @Captor
    private ArgumentCaptor<Map<String, Product>> multiPutCaptor;

    @BeforeEach
    void setUp() {
        productCacheReader = new ProductCacheReader(cacheRepository, productService);
    }

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
            var idPage = new ProductCacheReader.ProductIdPage(List.of(1L, 2L), true);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(idPage);
            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(List.of(product1, product2));

            // act
            Page<Product> result = productCacheReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

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
            var idPage = new ProductCacheReader.ProductIdPage(List.of(1L, 2L, 3L), false);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(idPage);
            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(Arrays.asList(product1, null, product3));

            var product2 = ProductFixture.createProduct(2L);
            given(productService.getActiveProductsByIds(List.of(2L)))
                    .willReturn(Map.of(2L, product2));

            // act
            var result = productCacheReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(3),
                    () -> assertThat(result.content().get(0).getId()).isEqualTo(1L),
                    () -> assertThat(result.content().get(1).getId()).isEqualTo(2L),
                    () -> assertThat(result.content().get(2).getId()).isEqualTo(3L),
                    () -> assertThat(result.hasNext()).isFalse()
            );
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), any(Supplier.class));
            assertThat(multiPutCaptor.getValue()).containsKey(ProductCacheConstants.DETAIL_KEY.of(2L));
        }

        @DisplayName("목록 캐시 MISS이면, 락을 획득하고 DB에서 조회한 뒤 양쪽 캐시에 저장한다.")
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
            var result = productCacheReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).containsExactly(product1, product2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
            then(cacheRepository).should().put(anyString(), any(ProductCacheReader.ProductIdPage.class), any(Duration.class));
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), any(Supplier.class));
            Map<String, Product> cached = multiPutCaptor.getValue();
            assertAll(
                    () -> assertThat(cached).containsKey(ProductCacheConstants.DETAIL_KEY.of(1L)),
                    () -> assertThat(cached).containsKey(ProductCacheConstants.DETAIL_KEY.of(2L))
            );
        }

        @DisplayName("목록 캐시 MISS + 락 대기 후 캐시 HIT이면, DB를 호출하지 않고 캐시에서 반환한다.")
        @Test
        void returnsCacheAfterLockWait_whenCachePopulatedByOtherThread() {
            // arrange
            var pageSize = new PageSize(0, 20);
            var product1 = ProductFixture.createProduct(1L);
            var idPage = new ProductCacheReader.ProductIdPage(List.of(1L), false);

            given(cacheRepository.get(anyString(), any(CacheType.class)))
                    .willReturn(null)
                    .willReturn(idPage);
            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(List.of(product1));

            // act
            var result = productCacheReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).containsExactly(product1),
                    () -> assertThat(result.hasNext()).isFalse()
            );
            then(productService).shouldHaveNoInteractions();
            then(cacheRepository).should(times(2)).get(anyString(), any(CacheType.class));
        }

        @DisplayName("캐시 불가 페이지(page > 2)이면, 목록 캐시를 저장하지 않지만 상세 캐시는 저장한다.")
        @Test
        void doesNotCacheList_whenPageExceedsMax() {
            // arrange
            var pageSize = new PageSize(3, 20);
            var product1 = ProductFixture.createProduct(1L);
            var dbPage = new Page<>(List.of(product1), false);

            given(productService.getActiveProducts(null, ProductSortType.DEFAULT, pageSize)).willReturn(dbPage);

            // act
            productCacheReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            then(cacheRepository).should(never()).put(anyString(), any(ProductCacheReader.ProductIdPage.class), any(Duration.class));
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), any(Supplier.class));
            assertThat(multiPutCaptor.getValue()).containsKey(ProductCacheConstants.DETAIL_KEY.of(1L));
        }
    }

    @DisplayName("ID 목록으로 활성 상품을 조회할 때,")
    @Nested
    class ReadActiveProductsByIds {

        @DisplayName("전체 캐시 HIT이면, DB를 호출하지 않고 입력 순서대로 반환한다.")
        @Test
        void returnsFromCacheInOrder_whenAllHit() {
            // arrange
            var product1 = ProductFixture.createProduct(1L);
            var product2 = ProductFixture.createProduct(2L);
            var product3 = ProductFixture.createProduct(3L);
            List<Long> ids = List.of(3L, 1L, 2L);

            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(List.of(product3, product1, product2));

            // act
            List<Product> result = productCacheReader.readActiveProductsByIds(ids);

            // assert
            assertAll(
                    () -> assertThat(result).hasSize(3),
                    () -> assertThat(result.get(0).getId()).isEqualTo(3L),
                    () -> assertThat(result.get(1).getId()).isEqualTo(1L),
                    () -> assertThat(result.get(2).getId()).isEqualTo(2L)
            );
            then(productService).shouldHaveNoInteractions();
        }

        @DisplayName("부분 캐시 MISS이면, 미스된 상품만 DB에서 조회하고 캐싱한다.")
        @Test
        void fetchesMissingFromDb_whenPartialMiss() {
            // arrange
            var product1 = ProductFixture.createProduct(1L);
            var product2 = ProductFixture.createProduct(2L);
            var product3 = ProductFixture.createProduct(3L);
            List<Long> ids = List.of(1L, 2L, 3L);

            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(Arrays.asList(product1, null, product3));
            given(productService.getActiveProductsByIds(List.of(2L)))
                    .willReturn(Map.of(2L, product2));

            // act
            List<Product> result = productCacheReader.readActiveProductsByIds(ids);

            // assert
            assertAll(
                    () -> assertThat(result).hasSize(3),
                    () -> assertThat(result.get(0).getId()).isEqualTo(1L),
                    () -> assertThat(result.get(1).getId()).isEqualTo(2L),
                    () -> assertThat(result.get(2).getId()).isEqualTo(3L)
            );
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), any(Supplier.class));
            assertThat(multiPutCaptor.getValue()).containsKey(ProductCacheConstants.DETAIL_KEY.of(2L));
        }

        @DisplayName("전체 캐시 MISS이면, 전체를 DB에서 조회하고 캐싱한다.")
        @Test
        void fetchesAllFromDb_whenAllMiss() {
            // arrange
            var product1 = ProductFixture.createProduct(1L);
            var product2 = ProductFixture.createProduct(2L);
            List<Long> ids = List.of(1L, 2L);

            given(cacheRepository.multiGet(anyList(), any(CacheType.class)))
                    .willReturn(Arrays.asList(null, null));
            given(productService.getActiveProductsByIds(ids))
                    .willReturn(Map.of(1L, product1, 2L, product2));

            // act
            List<Product> result = productCacheReader.readActiveProductsByIds(ids);

            // assert
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result.get(0).getId()).isEqualTo(1L),
                    () -> assertThat(result.get(1).getId()).isEqualTo(2L)
            );
            then(cacheRepository).should().multiPut(multiPutCaptor.capture(), any(Supplier.class));
            assertThat(multiPutCaptor.getValue()).hasSize(2);
        }

        @DisplayName("빈 ID 목록이면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenEmptyIds() {
            // act
            List<Product> result = productCacheReader.readActiveProductsByIds(List.of());

            // assert
            assertThat(result).isEmpty();
            then(cacheRepository).shouldHaveNoInteractions();
        }
    }

    @DisplayName("Lock 메모리 관리를 할 때,")
    @Nested
    class LockCleanup {

        @DisplayName("목록 캐시 MISS로 락을 사용한 뒤, 락이 map에서 제거된다.")
        @Test
        void removesLockAfterListCacheMiss() {
            // arrange
            var pageSize = new PageSize(0, 20);
            var product1 = ProductFixture.createProduct(1L);
            var dbPage = new Page<>(List.of(product1), false);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(null);
            given(productService.getActiveProducts(null, ProductSortType.DEFAULT, pageSize)).willReturn(dbPage);

            // act
            productCacheReader.readActiveProducts(null, ProductSortType.DEFAULT, pageSize);

            // assert
            assertThat(productCacheReader.lockCount()).isZero();
        }

        @DisplayName("상세 캐시 MISS로 락을 사용한 뒤, 락이 map에서 제거된다.")
        @Test
        void removesLockAfterDetailCacheMiss() {
            // arrange
            var product = ProductFixture.createProduct(1L);
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(1L);

            given(cacheRepository.get(eq(detailKey), any(CacheType.class))).willReturn(null);
            given(productService.getActiveProduct(1L)).willReturn(product);

            // act
            productCacheReader.readActiveProduct(1L);

            // assert
            assertThat(productCacheReader.lockCount()).isZero();
        }

        @DisplayName("여러 키로 락을 사용한 뒤, 모두 제거된다.")
        @Test
        void removesAllLocksAfterMultipleCalls() {
            // arrange
            var product1 = ProductFixture.createProduct(1L);
            var product2 = ProductFixture.createProduct(2L);

            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(null);
            given(productService.getActiveProduct(1L)).willReturn(product1);
            given(productService.getActiveProduct(2L)).willReturn(product2);

            // act
            productCacheReader.readActiveProduct(1L);
            productCacheReader.readActiveProduct(2L);

            // assert
            assertThat(productCacheReader.lockCount()).isZero();
        }
    }

    @DisplayName("활성 상품 단건을 조회할 때,")
    @Nested
    class ReadActiveProduct {

        @DisplayName("캐시 HIT이면, DB를 호출하지 않는다.")
        @Test
        void returnsFromCache_whenCacheHit() {
            // arrange
            var product = ProductFixture.createProduct(1L);
            given(cacheRepository.get(anyString(), any(CacheType.class))).willReturn(product);

            // act
            var result = productCacheReader.readActiveProduct(1L);

            // assert
            assertThat(result).isEqualTo(product);
            then(productService).shouldHaveNoInteractions();
        }

        @DisplayName("캐시 MISS이면, 락을 획득하고 DB에서 조회한 뒤 캐싱한다.")
        @Test
        void fetchesFromDbAndCaches_whenCacheMiss() {
            // arrange
            var product = ProductFixture.createProduct(1L);
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(1L);

            given(cacheRepository.get(eq(detailKey), any(CacheType.class))).willReturn(null);
            given(productService.getActiveProduct(1L)).willReturn(product);

            // act
            var result = productCacheReader.readActiveProduct(1L);

            // assert
            assertAll(
                    () -> assertThat(result).isEqualTo(product),
                    () -> then(cacheRepository).should().put(eq(detailKey), eq(product), any(Duration.class))
            );
        }

        @DisplayName("캐시 MISS + 락 대기 후 캐시 HIT이면, DB를 호출하지 않고 캐시에서 반환한다.")
        @Test
        void returnsCacheAfterLockWait_whenCachePopulatedByOtherThread() {
            // arrange
            var product = ProductFixture.createProduct(1L);
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(1L);

            given(cacheRepository.get(eq(detailKey), any(CacheType.class)))
                    .willReturn(null)
                    .willReturn(product);

            // act
            var result = productCacheReader.readActiveProduct(1L);

            // assert
            assertAll(
                    () -> assertThat(result).isEqualTo(product),
                    () -> then(productService).shouldHaveNoInteractions(),
                    () -> then(cacheRepository).should(times(2)).get(eq(detailKey), any(CacheType.class))
            );
        }
    }
}
