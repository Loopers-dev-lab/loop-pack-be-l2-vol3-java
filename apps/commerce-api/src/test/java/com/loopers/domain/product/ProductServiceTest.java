package com.loopers.domain.product;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.vo.DisplayStatus;
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

    @DisplayName("상품 생성")
    @Nested
    class CreateProduct {

        @DisplayName("정상적으로 상품을 생성한다")
        @Test
        void createsProduct_andReturnsSaved() {
            // arrange
            ProductCommand.Create command = new ProductCommand.Create(1L, "운동화", 50000, 100);
            Product saved = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
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
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
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
                    "슬리퍼", 20000, 50,
                    com.loopers.domain.product.vo.DisplayStatus.DISPLAYING
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
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
            ProductCommand.Update command = new ProductCommand.Update(
                    "슬리퍼", 20000, 50,
                    com.loopers.domain.product.vo.DisplayStatus.DISPLAYING
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
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
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
            Product product = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);

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
            Product product1 = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
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
            Product product1 = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
            Product product2 = Product.reconstruct(2L, 1L, "슬리퍼", 20000, 50, DisplayStatus.DISPLAYING);
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
            Product product1 = Product.reconstruct(1L, 1L, "운동화", 50000, 100, DisplayStatus.DISPLAYING);
            Product product2 = Product.reconstruct(2L, 1L, "슬리퍼", 20000, 50, DisplayStatus.DISPLAYING);
            Page<Product> page = new PageImpl<>(List.of(product1, product2));
            when(productRepository.findAll(Pageable.unpaged(), 1L)).thenReturn(page);

            // act
            List<Product> result = productService.findProductsByBrandId(1L);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getBrandId()).isEqualTo(1L);
        }
    }

    @DisplayName("상품 목록 조회 (커스텀)")
    @Nested
    class FindProductList {

        @DisplayName("정상적으로 상품 목록을 조회한다")
        @Test
        void returnsProductItemPage_forGivenConditions() {
            // arrange
            ProductItem item1 = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 5L, false);
            ProductItem item2 = new ProductItem(2L, "슬리퍼", 1L, "나이키", 20000, 50, "DISPLAYING", 2L, true);
            Page<ProductItem> page = new PageImpl<>(List.of(item1, item2));
            Pageable pageable = PageRequest.of(0, 10);
            when(productCustomRepository.findProductList(1L, 10L, SortFilter.LATEST, pageable)).thenReturn(page);

            // act
            Page<ProductItem> result = productService.findProductList(1L, 10L, SortFilter.LATEST, pageable);

            // assert
            verify(productCustomRepository).findProductList(1L, 10L, SortFilter.LATEST, pageable);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).id()).isEqualTo(1L);
            assertThat(result.getContent().get(1).id()).isEqualTo(2L);
        }
    }

    @DisplayName("상품 상세 조회 (회원 포함)")
    @Nested
    class FindProductWithMemberId {

        @DisplayName("존재하지 않는 상품이면 예외가 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            when(productCustomRepository.findProduct(999L, 10L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.findProduct(999L, 10L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 상품 상세를 조회한다")
        @Test
        void returnsProductItem_whenFound() {
            // arrange
            ProductItem item = new ProductItem(1L, "운동화", 1L, "나이키", 50000, 100, "DISPLAYING", 3L, true);
            when(productCustomRepository.findProduct(1L, 10L)).thenReturn(Optional.of(item));

            // act
            ProductItem result = productService.findProduct(1L, 10L);

            // assert
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("운동화");
            assertThat(result.isFavorite()).isTrue();
        }
    }
}
