package com.loopers.domain.product;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.vo.DisplayStatus;
import com.loopers.domain.product.repository.ProductCacheRepository;
import com.loopers.domain.product.repository.ProductCustomRepository;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.enums.SortFilter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCustomRepository productCustomRepository;

    @Mock
    private ProductCacheRepository productCacheRepository;

    @DisplayName("상품 생성")
    @Nested
    class CreateProduct {

        @DisplayName("정상적으로 상품을 생성한다")
        @Test
        void createsProduct_andReturnsSaved() {
            // arrange
            ProductCommand.Create command = new ProductCommand.Create(1L, "운동화", 50000, 100);
            Product saved = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.save(any(Product.class))).thenReturn(saved);

            // act
            Product result = productService.createProduct(1L, command);

            // assert
            verify(productRepository).save(any(Product.class));
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName().value()).isEqualTo("운동화");
        }
    }

    @DisplayName("상품 조회")
    @Nested
    class FindProduct {

        @DisplayName("존재하지 않는 상품 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.findProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 상품을 조회한다")
        @Test
        void returnsProduct_whenFound() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            Product result = productService.findProduct(1L);

            // assert
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName().value()).isEqualTo("운동화");
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class UpdateProduct {

        @DisplayName("존재하지 않는 상품 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            ProductCommand.Update command = new ProductCommand.Update(
                    "슬리퍼", 20000, 50, DisplayStatus.DISPLAYING
            );
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.updateProduct(999L, command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 상품을 수정한다")
        @Test
        void updatesProduct_andCallsUpdate() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            ProductCommand.Update command = new ProductCommand.Update(
                    "슬리퍼", 20000, 50, DisplayStatus.DISPLAYING
            );
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            Product result = productService.updateProduct(1L, command);

            // assert
            verify(productRepository).update(product);
            assertThat(result.getName().value()).isEqualTo("슬리퍼");
            assertThat(result.getPrice().value()).isEqualTo(20000);
        }
    }

    @DisplayName("상품 삭제")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하지 않는 상품 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 상품을 삭제한다")
        @Test
        void deletesProduct_andCallsDeleteById() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            productService.deleteProduct(1L);

            // assert
            verify(productRepository).deleteById(1L);
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class DecreaseStock {

        @DisplayName("정상적으로 재고를 차감하고 update를 호출한다")
        @Test
        void decreasesStock_andCallsUpdate() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);

            // act
            productService.decreaseStock(product, 10);

            // assert
            verify(productRepository).update(product);
            assertThat(product.getStock().value()).isEqualTo(90);
        }
    }

    @DisplayName("ID 목록 조회")
    @Nested
    class GetProductsByIds {

        @DisplayName("요청한 ID 중 일부가 존재하지 않으면 예외가 발생한다")
        @Test
        void throwsException_whenSomeIdsNotFound() {
            // arrange
            List<Long> ids = List.of(1L, 2L, 3L);
            Product product1 = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.findByIds(ids)).thenReturn(List.of(product1));

            // act & assert
            assertThatThrownBy(() -> productService.getProductsByIds(ids))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 ID 목록에 해당하는 상품 목록을 반환한다")
        @Test
        void returnsProducts_whenAllIdsFound() {
            // arrange
            List<Long> ids = List.of(1L, 2L);
            Product product1 = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            Product product2 = Product.reconstruct(2L, 1L, "슬리퍼", 20000, 50, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.findByIds(ids)).thenReturn(List.of(product1, product2));

            // act
            List<Product> result = productService.getProductsByIds(ids);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(1).getId()).isEqualTo(2L);
        }
    }

    @DisplayName("브랜드별 조회")
    @Nested
    class FindProductsByBrandId {

        @DisplayName("정상적으로 브랜드별 상품 목록을 반환한다")
        @Test
        void returnsProducts_forGivenBrandId() {
            // arrange
            Product product1 = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING, 0L);
            Product product2 = Product.reconstruct(2L, 1L, "슬리퍼", 20000, 50, DisplayStatus.DISPLAYING, 0L);
            Page<Product> page = new PageImpl<>(List.of(product1, product2));
            when(productRepository.findAll(Pageable.unpaged(), 1L)).thenReturn(page);

            // act
            List<Product> result = productService.findProductsByBrandId(1L);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getBrandId()).isEqualTo(1L);
        }
    }

    @DisplayName("상품 목록 조회 (캐시 포함)")
    @Nested
    class FindProductList {

        @DisplayName("브랜드 지정 시 캐시를 사용하지 않고 DB에서 조회한다")
        @Test
        void skipsCache_whenBrandIdPresent() {
            // arrange
            ProductItem item = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 5L, false);
            Page<ProductItem> page = new PageImpl<>(List.of(item));
            Pageable pageable = PageRequest.of(0, 10);
            when(productCustomRepository.findProductList(1L, SortFilter.LATEST, pageable)).thenReturn(page);

            // act
            Page<ProductItem> result = productService.findProductList(1L, SortFilter.LATEST, pageable);

            // assert
            verify(productCacheRepository, never()).getFirstPage();
            assertThat(result.getContent()).hasSize(1);
        }

        @DisplayName("첫 페이지 캐시 히트 시 캐시에서 반환하고 likeCount를 resolve한다")
        @Test
        void returnsCachedPage_whenFirstPageCacheHit() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            ProductItem cachedItem = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 0L, false);
            ProductCacheRepository.CachedPage cached = new ProductCacheRepository.CachedPage(List.of(cachedItem), 100);

            when(productCacheRepository.getFirstPage()).thenReturn(Optional.of(cached));
            when(productCacheRepository.getLikeCount(1L)).thenReturn(Optional.of(5L));

            // act
            Page<ProductItem> result = productService.findProductList(null, SortFilter.LATEST, pageable);

            // assert
            verify(productCustomRepository, never()).findProductList(any(), any(), any());
            assertThat(result.getContent().get(0).favoriteCnt()).isEqualTo(5L);
            assertThat(result.getTotalElements()).isEqualTo(100);
        }

        @DisplayName("첫 페이지 캐시 미스 시 DB 조회 후 캐시에 저장한다")
        @Test
        void savesToCache_whenFirstPageCacheMiss() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            ProductItem item = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 5L, false);
            Page<ProductItem> page = new PageImpl<>(List.of(item), pageable, 1);

            when(productCacheRepository.getFirstPage()).thenReturn(Optional.empty());
            when(productCustomRepository.findProductList(null, SortFilter.LATEST, pageable)).thenReturn(page);

            // act
            productService.findProductList(null, SortFilter.LATEST, pageable);

            // assert
            verify(productCacheRepository).putFirstPage(List.of(item), 1);
            verify(productCacheRepository).initLikeCountIfAbsent(1L, 5L);
        }
    }

    @DisplayName("상품 상세 조회 (캐시 포함)")
    @Nested
    class FindProductDetail {

        @DisplayName("캐시 히트 시 캐시에서 반환하고 likeCount를 resolve한다")
        @Test
        void returnsCachedItem_whenCacheHit() {
            // arrange
            ProductItem cachedItem = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 0L, false);
            when(productCacheRepository.get(1L)).thenReturn(Optional.of(cachedItem));
            when(productCacheRepository.getLikeCount(1L)).thenReturn(Optional.of(10L));

            // act
            ProductItem result = productService.findProductDetail(1L);

            // assert
            assertThat(result.favoriteCnt()).isEqualTo(10L);
            verify(productCustomRepository, never()).findProduct(anyLong());
        }

        @DisplayName("캐시 미스 시 DB 조회 후 캐시에 저장한다")
        @Test
        void savesToCache_whenCacheMiss() {
            // arrange
            ProductItem item = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 3L, false);
            when(productCacheRepository.get(1L)).thenReturn(Optional.empty());
            when(productCustomRepository.findProduct(1L)).thenReturn(Optional.of(item));

            // act
            ProductItem result = productService.findProductDetail(1L);

            // assert
            assertThat(result.favoriteCnt()).isEqualTo(3L);
            verify(productCacheRepository).put(1L, item);
            verify(productCacheRepository).initLikeCountIfAbsent(1L, 3L);
        }

        @DisplayName("존재하지 않는 상품이면 예외가 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            when(productCacheRepository.get(999L)).thenReturn(Optional.empty());
            when(productCustomRepository.findProduct(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.findProductDetail(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @DisplayName("좋아요 수 증감")
    @Nested
    class LikeCount {

        @DisplayName("increaseLikeCount는 DB와 캐시 카운터를 모두 증가시킨다")
        @Test
        void increaseLikeCount_updatesBothDbAndCache() {
            // act
            productService.increaseLikeCount(1L);

            // assert
            verify(productRepository).increaseLikeCount(1L);
            verify(productCacheRepository).incrementLikeCount(1L);
        }

        @DisplayName("decreaseLikeCount는 DB와 캐시 카운터를 모두 감소시킨다")
        @Test
        void decreaseLikeCount_updatesBothDbAndCache() {
            // act
            productService.decreaseLikeCount(1L);

            // assert
            verify(productRepository).decreaseLikeCount(1L);
            verify(productCacheRepository).decrementLikeCount(1L);
        }
    }

    @DisplayName("원자적 재고 차감")
    @Nested
    class DecreaseStockAtomic {

        @DisplayName("정상 차감 시 캐시를 무효화한다")
        @Test
        void evictsCache_onSuccess() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 90, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.decreaseStock(1L, 10)).thenReturn(1);

            // act
            productService.decreaseStockAtomic(1L, 10);

            // assert
            verify(productCacheRepository).evict(1L);
            verify(productCacheRepository).evictFirstPage();
        }

        @DisplayName("재고 부족 시 예외가 발생한다")
        @Test
        void throwsException_whenInsufficientStock() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 5, DisplayStatus.DISPLAYING, 0L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.decreaseStock(1L, 10)).thenReturn(0);

            // act & assert
            assertThatThrownBy(() -> productService.decreaseStockAtomic(1L, 10))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
