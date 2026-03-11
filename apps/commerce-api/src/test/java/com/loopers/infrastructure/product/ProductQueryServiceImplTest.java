package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductPageReadCache;
import com.loopers.application.product.ProductReadCache;
import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProductQueryServiceImplTest {

    private ProductJpaRepository productJpaRepository;
    private ProductReadCache productReadCache;
    private ProductPageReadCache productPageReadCache;
    private ProductQueryServiceImpl productQueryService;

    @BeforeEach
    void setUp() {
        productJpaRepository = mock(ProductJpaRepository.class);
        productReadCache = mock(ProductReadCache.class);
        productPageReadCache = mock(ProductPageReadCache.class);
        productQueryService = new ProductQueryServiceImpl(
            productJpaRepository, productReadCache, productPageReadCache
        );
    }

    @DisplayName("getById를 호출할 때, ")
    @Nested
    class GetById {

        @DisplayName("캐시에서 값을 반환하면, 그대로 반환한다.")
        @Test
        void returnsValue_whenCacheReturnsValue() {
            // given
            ProductReadModel readModel = new ProductReadModel(
                1L, 1L, "에어맥스", 129000, 0, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(productReadCache.get(eq(1L), any(Supplier.class))).willReturn(readModel);

            // when
            ProductReadModel result = productQueryService.getById(1L);

            // then
            assertThat(result.name()).isEqualTo("에어맥스");
            assertThat(result.price()).isEqualTo(129000);
            verify(productReadCache).get(eq(1L), any(Supplier.class));
        }

        @DisplayName("캐시에서 null을 반환하면, CoreException을 던진다.")
        @Test
        void throwsNotFound_whenCacheReturnsNull() {
            // given
            given(productReadCache.get(eq(999L), any(Supplier.class))).willReturn(null);

            // when & then
            assertThatThrownBy(() -> productQueryService.getById(999L))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("전체 필드가 보존된다.")
        @Test
        void preservesAllFields() {
            // given
            ZonedDateTime now = ZonedDateTime.now();
            ProductReadModel readModel = new ProductReadModel(1L, 2L, "에어맥스", 129000, 5, now, now);
            given(productReadCache.get(eq(1L), any(Supplier.class))).willReturn(readModel);

            // when
            ProductReadModel result = productQueryService.getById(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.brandId()).isEqualTo(2L);
            assertThat(result.name()).isEqualTo("에어맥스");
            assertThat(result.price()).isEqualTo(129000);
            assertThat(result.likeCount()).isEqualTo(5);
            assertThat(result.createdAt()).isEqualTo(now);
            assertThat(result.updatedAt()).isEqualTo(now);
        }
    }

    @DisplayName("getAll을 호출할 때, ")
    @Nested
    class GetAll {

        @DisplayName("brandId가 null이면, 페이지 캐시를 사용한다.")
        @Test
        void usesPageCache_whenBrandIdIsNull() {
            // given
            PageResult<ProductReadModel> cachedPage = new PageResult<>(List.of(), 0, 20, 0, 0);
            given(productPageReadCache.get(
                eq(ProductSortType.LATEST), eq(0), eq(20), any(Supplier.class)
            )).willReturn(cachedPage);

            // when
            PageResult<ProductReadModel> result = productQueryService.getAll(
                null, ProductSortType.LATEST, 0, 20
            );

            // then
            assertThat(result).isEqualTo(cachedPage);
            verify(productPageReadCache).get(
                eq(ProductSortType.LATEST), eq(0), eq(20), any(Supplier.class)
            );
        }

        @DisplayName("brandId가 있으면, 페이지 캐시를 사용하지 않고 DB에서 직접 조회한다.")
        @Test
        void queriesDbDirectly_whenBrandIdIsNotNull() {
            // given
            Page<Product> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 0
            );
            given(productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(
                eq(1L), any(PageRequest.class)
            )).willReturn(emptyPage);

            // when
            PageResult<ProductReadModel> result = productQueryService.getAll(
                1L, ProductSortType.LATEST, 0, 20
            );

            // then
            assertThat(result.items()).isEmpty();
            verify(productPageReadCache, never()).get(
                any(ProductSortType.class), anyInt(), anyInt(), any(Supplier.class)
            );
        }
    }
}
