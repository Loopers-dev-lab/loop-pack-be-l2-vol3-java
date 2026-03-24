package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.support.page.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductQueryServiceTest {

    BrandRepository brandRepository = mock(BrandRepository.class);
    ProductRepository productRepository = mock(ProductRepository.class);
    ProductAssembler productAssembler = new ProductAssembler();
    ProductCacheStore productCacheStore = mock(ProductCacheStore.class);
    ProductQueryService productQueryService = new ProductQueryService(productRepository, brandRepository, productAssembler, productCacheStore);

    @DisplayName("상품 목록 조회 시, ")
    @Nested
    class GetList {

        @DisplayName("목록 캐시 HIT + detail 전체 HIT 시, DB 조회 없이 캐시된 상품 목록을 반환한다.")
        @Test
        void returnsCachedList_whenListCacheHitAndAllDetailCacheHit() {
            // arrange
            Long brandId = 1L;
            Long productId = 10L;
            ProductInfo cachedInfo = new ProductInfo(productId, "나이키 에어맥스", "설명", 10, 150000, "나이키", 0L);

            when(productCacheStore.getList(brandId, "latest", 0, 20))
                    .thenReturn(Optional.of(new ProductCacheStore.ProductListCacheEntry(List.of(productId), 1)));
            when(productCacheStore.multiGet(List.of(productId)))
                    .thenReturn(Map.of(productId, cachedInfo));

            // act
            PageResponse<ProductInfo> result = productQueryService.getList(PageRequest.of(0, 20), brandId, "latest");

            // assert
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).name()).isEqualTo("나이키 에어맥스");
            verify(productRepository, never()).findAllByBrandId(any(), any());
            verify(productRepository, never()).findAllByIdIn(any());
        }

        @DisplayName("목록 캐시 HIT + detail 부분 MISS 시, 미스된 id 만 DB 조회 후 캐시에 저장한다.")
        @Test
        void fetchesMissingAndPutsCache_whenListCacheHitAndDetailPartialMiss() {
            // arrange
            Long brandId = 1L;
            Long cachedProductId = 10L;
            Long missingProductId = 20L;
            ProductInfo cachedInfo = new ProductInfo(cachedProductId, "나이키 에어맥스", "설명", 10, 150000, "나이키", 0L);

            Product missingProduct = mock(Product.class);
            when(missingProduct.getId()).thenReturn(missingProductId);
            when(missingProduct.brandId()).thenReturn(brandId);
            when(missingProduct.name()).thenReturn("나이키 줌");
            when(missingProduct.description()).thenReturn("설명");
            when(missingProduct.stock()).thenReturn(Stock.from(5));
            when(missingProduct.price()).thenReturn(Price.from(100000));
            when(missingProduct.likeCount()).thenReturn(0L);

            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            when(productCacheStore.getList(brandId, "latest", 0, 20))
                    .thenReturn(Optional.of(new ProductCacheStore.ProductListCacheEntry(List.of(cachedProductId, missingProductId), 1)));
            when(productCacheStore.multiGet(List.of(cachedProductId, missingProductId)))
                    .thenReturn(Map.of(cachedProductId, cachedInfo));
            when(productRepository.findAllByIdIn(List.of(missingProductId))).thenReturn(List.of(missingProduct));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of(brand));

            // act
            PageResponse<ProductInfo> result = productQueryService.getList(PageRequest.of(0, 20), brandId, "latest");

            // assert
            assertThat(result.content()).hasSize(2);
            verify(productRepository).findAllByIdIn(List.of(missingProductId));
            verify(productCacheStore).put(eq(missingProductId), any(ProductInfo.class));
        }

        @DisplayName("목록 캐시 MISS 시, DB 조회 후 putList 와 put 을 각 상품마다 호출한다.")
        @Test
        void callsPutListAndPutEach_whenListCacheMiss() {
            // arrange
            Long brandId = 1L;
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(150000), brandId);
            PageRequest pageRequest = PageRequest.of(0, 20, com.loopers.domain.product.ProductSortType.LATEST.getSort());

            when(productCacheStore.getList(brandId, "latest", 0, 20)).thenReturn(Optional.empty());
            when(productRepository.findAllByBrandId(brandId, pageRequest)).thenReturn(new PageResponse<>(List.of(product), 0, 20, 1));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of(brand));

            // act
            productQueryService.getList(PageRequest.of(0, 20), brandId, "latest");

            // assert
            verify(productCacheStore).putList(eq(brandId), eq("latest"), eq(0), eq(20), eq(List.of(product.getId())), eq(1));
            verify(productCacheStore).put(eq(product.getId()), any(ProductInfo.class));
        }
    }

    @DisplayName("상품 상세 조회 시, ")
    @Nested
    class GetDetail {

        @DisplayName("캐시 HIT 시, DB 조회 없이 캐시된 ProductInfo 를 반환한다.")
        @Test
        void returnsCachedProductInfo_whenCacheHit() {
            // arrange
            Long productId = 1L;
            ProductInfo cached = new ProductInfo(productId, "나이키 에어맥스", "설명", 10, 150000, "나이키", 0L);
            when(productCacheStore.get(productId)).thenReturn(Optional.of(cached));

            // act
            ProductInfo result = productQueryService.getDetail(productId);

            // assert
            assertThat(result.name()).isEqualTo("나이키 에어맥스");
            assertThat(result.brand()).isEqualTo("나이키");
            verify(productRepository, never()).findById(productId);
        }

        @DisplayName("캐시 MISS 시, DB 에서 조회 후 캐시에 저장한다.")
        @Test
        void savesToCache_whenCacheMiss() {
            // arrange
            Long productId = 1L;
            Long brandId = 1L;
            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(150000), brandId);
            Brand brand = Brand.of("나이키", null);

            when(productCacheStore.get(productId)).thenReturn(Optional.empty());
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            // act
            productQueryService.getDetail(productId);

            // assert
            verify(productCacheStore).put(eq(productId), any(ProductInfo.class));
        }
    }
}
