package com.loopers.application.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortCondition;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ProductAppService 단위 테스트")
class ProductAppServiceTest {

    private ProductAppService productAppService;
    private ProductRepository productRepository;
    private OptionRepository optionRepository;
    private ProductCacheManager productCacheManager;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        optionRepository = mock(OptionRepository.class);
        productCacheManager = mock(ProductCacheManager.class);
        productAppService = new ProductAppService(productRepository, optionRepository, productCacheManager);
    }

    @Nested
    @DisplayName("상품 생성")
    class CreateProductTest {

        @Test
        @DisplayName("유효한 정보로 상품을 생성할 수 있다")
        void create_success() {
            // given
            Product savedProduct = mock(Product.class);
            given(savedProduct.getId()).willReturn(1L);
            given(savedProduct.getName()).willReturn("테스트 상품");
            given(productRepository.save(any(Product.class))).willReturn(savedProduct);

            // when
            Product result = productAppService.create(1L, "테스트 상품", Money.of(10000L));

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 상품");
            verify(productRepository).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("상품 조회")
    class GetByIdTest {

        @Test
        @DisplayName("ID로 상품을 조회할 수 있다")
        void getById_found() {
            // given
            Product product = mock(Product.class);
            given(product.getId()).willReturn(1L);
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            // when
            Product result = productAppService.getById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 예외가 발생한다")
        void getById_notFound() {
            // given
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productAppService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("상품 목록 조회")
    class GetProductsTest {

        @Test
        @DisplayName("정렬 조건으로 상품 목록을 조회할 수 있다")
        void getProducts() {
            // given
            Product p1 = mock(Product.class);
            Product p2 = mock(Product.class);
            given(productRepository.findAll(ProductSortCondition.LATEST)).willReturn(List.of(p1, p2));

            // when
            List<Product> result = productAppService.getProducts(ProductSortCondition.LATEST);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("옵션 조회")
    class GetOptionTest {

        @Test
        @DisplayName("ID로 옵션을 조회할 수 있다")
        void getOptionById_found() {
            // given
            Option option = mock(Option.class);
            given(option.getId()).willReturn(1L);
            given(optionRepository.findById(1L)).willReturn(Optional.of(option));

            // when
            Option result = productAppService.getOptionById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 옵션 ID로 조회하면 예외가 발생한다")
        void getOptionById_notFound() {
            // given
            given(optionRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productAppService.getOptionById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("옵션을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("배치 조회")
    class BatchQueryTest {

        @Test
        @DisplayName("상품 ID 목록으로 옵션 Map을 조회할 수 있다")
        void getOptionsByProductIds() {
            // given
            List<Long> productIds = List.of(1L, 2L);
            Option o1 = mock(Option.class);
            given(o1.getProductId()).willReturn(1L);
            Option o2 = mock(Option.class);
            given(o2.getProductId()).willReturn(1L);
            Option o3 = mock(Option.class);
            given(o3.getProductId()).willReturn(2L);
            given(optionRepository.findByProductIdIn(productIds)).willReturn(List.of(o1, o2, o3));

            // when
            Map<Long, List<Option>> result = productAppService.getOptionsByProductIds(productIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L)).hasSize(2);
            assertThat(result.get(2L)).hasSize(1);
        }

        @Test
        @DisplayName("옵션 ID 목록으로 옵션 Map을 조회할 수 있다")
        void getOptionsByIds() {
            // given
            List<Long> optionIds = List.of(1L, 2L);
            Option o1 = mock(Option.class);
            given(o1.getId()).willReturn(1L);
            given(o1.getName()).willReturn("옵션A");
            Option o2 = mock(Option.class);
            given(o2.getId()).willReturn(2L);
            given(o2.getName()).willReturn("옵션B");
            given(optionRepository.findByIdIn(optionIds)).willReturn(List.of(o1, o2));

            // when
            Map<Long, Option> result = productAppService.getOptionsByIds(optionIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getName()).isEqualTo("옵션A");
            assertThat(result.get(2L).getName()).isEqualTo("옵션B");
        }

        @Test
        @DisplayName("상품 ID 목록으로 상품 Map을 조회할 수 있다")
        void getByIds() {
            // given
            List<Long> productIds = List.of(1L, 2L);
            Product p1 = mock(Product.class);
            given(p1.getId()).willReturn(1L);
            given(p1.getName()).willReturn("상품A");
            Product p2 = mock(Product.class);
            given(p2.getId()).willReturn(2L);
            given(p2.getName()).willReturn("상품B");
            given(productRepository.findByIdIn(productIds)).willReturn(List.of(p1, p2));

            // when
            Map<Long, Product> result = productAppService.getByIds(productIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getName()).isEqualTo("상품A");
        }
    }

    @Nested
    @DisplayName("재고 차감")
    class DecreaseStockTest {

        @Test
        @DisplayName("재고를 차감할 수 있다")
        void decreaseStock_success() {
            // given
            Option option = mock(Option.class);
            given(option.getStock()).willReturn(90);
            given(optionRepository.findByIdWithLock(1L)).willReturn(Optional.of(option));

            // when
            Option result = productAppService.decreaseStock(1L, 10);

            // then
            assertThat(result.getStock()).isEqualTo(90);
            verify(option).decreaseStock(10);
        }

        @Test
        @DisplayName("재고보다 많은 수량을 차감하면 예외가 발생한다")
        void decreaseStock_insufficientStock() {
            // given
            Option option = mock(Option.class);
            doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                    .when(option).decreaseStock(10);
            given(optionRepository.findByIdWithLock(1L)).willReturn(Optional.of(option));

            // when & then
            assertThatThrownBy(() -> productAppService.decreaseStock(1L, 10))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("재고가 부족합니다.");
        }
    }

    @Nested
    @DisplayName("재고 복원")
    class IncreaseStockTest {

        @Test
        @DisplayName("재고를 복원할 수 있다")
        void increaseStock_success() {
            // given
            Option option = mock(Option.class);
            given(option.getStock()).willReturn(100);
            given(optionRepository.findByIdWithLock(1L)).willReturn(Optional.of(option));

            // when
            Option result = productAppService.increaseStock(1L, 10);

            // then
            assertThat(result.getStock()).isEqualTo(100);
            verify(option).increaseStock(10);
        }
    }

    @Nested
    @DisplayName("브랜드 상품 부분 캐싱")
    class BrandProductCacheTest {

        @Test
        @DisplayName("page 0~2 캐시 히트 시 Repository를 호출하지 않는다")
        void cacheHit_withinLimit_skipsRepository() {
            // given
            int page = 1;
            CachedBrandProductPage cachedPage = CachedBrandProductPage.builder()
                    .content(List.of())
                    .totalElements(0L)
                    .build();
            given(productCacheManager.getProductList(1L, page, 20))
                    .willReturn(Optional.of(cachedPage));

            // when
            CachedBrandProductPage result = productAppService.getProductsByBrandIdCached(1L, page, 20);

            // then
            assertThat(result).isEqualTo(cachedPage);
            verify(productRepository, never()).findByBrandIdWithPaging(anyLong(), any(PageRequest.class));
        }

        @Test
        @DisplayName("page 0~2 캐시 미스 시 DB 조회 후 캐시에 저장한다")
        void cacheMiss_withinLimit_queriesDbAndCaches() {
            // given
            int page = 0;
            given(productCacheManager.getProductList(1L, page, 20))
                    .willReturn(Optional.empty());

            Page<Product> dbPage = new PageImpl<>(List.of(), PageRequest.of(page, 20), 0);
            given(productRepository.findByBrandIdWithPaging(eq(1L), any(PageRequest.class)))
                    .willReturn(dbPage);

            // when
            productAppService.getProductsByBrandIdCached(1L, page, 20);

            // then
            verify(productRepository).findByBrandIdWithPaging(eq(1L), any(PageRequest.class));
            verify(productCacheManager).putProductList(eq(1L), eq(page), eq(20), any(CachedBrandProductPage.class));
        }

        @Test
        @DisplayName("캐시 조회에서 예외 발생 시 DB fallback으로 정상 응답한다")
        void cacheException_fallsBackToDb() {
            // given
            int page = 0;
            given(productCacheManager.getProductList(1L, page, 20))
                    .willThrow(new RuntimeException("Redis connection refused"));

            Page<Product> dbPage = new PageImpl<>(List.of(), PageRequest.of(page, 20), 0);
            given(productRepository.findByBrandIdWithPaging(eq(1L), any(PageRequest.class)))
                    .willReturn(dbPage);

            // when
            CachedBrandProductPage result = productAppService.getProductsByBrandIdCached(1L, page, 20);

            // then
            assertThat(result).isNotNull();
            verify(productRepository).findByBrandIdWithPaging(eq(1L), any(PageRequest.class));
        }

        @Test
        @DisplayName("page 3 이상은 캐시를 사용하지 않고 DB에서 직접 조회한다")
        void deepPage_bypassesCache() {
            // given
            int page = 3;
            Page<Product> dbPage = new PageImpl<>(List.of(), PageRequest.of(page, 20), 0);
            given(productRepository.findByBrandIdWithPaging(eq(1L), any(PageRequest.class)))
                    .willReturn(dbPage);

            // when
            productAppService.getProductsByBrandIdCached(1L, page, 20);

            // then
            verify(productCacheManager, never()).getProductList(anyLong(), anyInt(), anyInt());
            verify(productCacheManager, never()).putProductList(anyLong(), anyInt(), anyInt(), any(CachedBrandProductPage.class));
            verify(productRepository).findByBrandIdWithPaging(eq(1L), any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("옵션 생성")
    class CreateOptionTest {

        @Test
        @DisplayName("옵션을 생성할 수 있다")
        void createOption_success() {
            // given
            Product product = mock(Product.class);
            given(product.getId()).willReturn(1L);
            Option savedOption = mock(Option.class);
            given(savedOption.getId()).willReturn(1L);
            given(savedOption.getName()).willReturn("새 옵션");

            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(optionRepository.save(any(Option.class))).willReturn(savedOption);

            // when
            Option result = productAppService.createOption(1L, "새 옵션", Money.of(500L), 50);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("새 옵션");
            verify(optionRepository).save(any(Option.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품에 옵션을 생성하면 예외가 발생한다")
        void createOption_productNotFound() {
            // given
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productAppService.createOption(999L, "옵션", Money.of(0L), 10))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다.");
        }
    }
}
