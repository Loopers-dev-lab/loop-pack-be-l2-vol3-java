package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        productService = new ProductService(productRepository);
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_생성된_상품이_반환된다() {
            // arrange
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Product product = productService.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // assert
            assertThat(product)
                    .extracting(Product::getName, Product::getStatus, Product::getLikeCount)
                    .containsExactly("에어맥스", ProductStatus.ACTIVE, 0);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            productService.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // assert
            verify(productRepository).save(any(Product.class));
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class 단건조회 {

        @Test
        void 존재하지_않는_ID면_예외가_발생한다() {
            // arrange
            when(productRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.getById(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
        }

        @Test
        void 삭제된_상품이면_예외가_발생한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.delete();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act & assert
            assertThatThrownBy(() -> productService.getById(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ProductErrorType.ALREADY_DELETED);
        }

        @Test
        void 존재하는_상품이면_반환한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            Product result = productService.getById(1L);

            // assert
            assertThat(result.getName()).isEqualTo("에어맥스");
        }
    }

    @DisplayName("노출 가능한 상품을 조회할 때,")
    @Nested
    class 노출가능상품조회 {

        @Test
        void 삭제된_상품이면_404_예외가_발생한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.delete();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act & assert
            assertThatThrownBy(() -> productService.getDisplayableProduct(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
        }

        @Test
        void 고객에게_노출_불가한_상품이면_404_예외가_발생한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.changeStatus(ProductStatus.HIDDEN);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act & assert
            assertThatThrownBy(() -> productService.getDisplayableProduct(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
        }

        @Test
        void 노출_가능한_상품이면_반환한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            Product result = productService.getDisplayableProduct(1L);

            // assert
            assertThat(result.getName()).isEqualTo("에어맥스");
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class 수정 {

        @Test
        void 유효한_정보면_수정된_상품이_반환된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Product result = productService.update(1L, "에어포스", "나이키 에어포스", 120000);

            // assert
            assertThat(result)
                    .extracting(Product::getName, Product::getDescription, Product::getBasePrice)
                    .containsExactly("에어포스", "나이키 에어포스", 120000);
        }
    }

    @DisplayName("상품 상태를 변경할 때,")
    @Nested
    class 상태변경 {

        @Test
        void 지정한_상태로_변경된_상품이_반환된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Product result = productService.changeStatus(1L, ProductStatus.SOLDOUT);

            // assert
            assertThat(result.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 존재하지_않는_ID면_예외가_발생한다() {
            // arrange
            when(productRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> productService.delete(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
        }

        @Test
        void 유효한_상품이면_delete가_호출된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            productService.delete(1L);

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("좋아요 수를 증가할 때,")
    @Nested
    class 좋아요증가 {

        @Test
        void 존재하는_상품이면_incrementLikeCount가_호출된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            productService.incrementLikeCount(1L);

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 수를 감소할 때,")
    @Nested
    class 좋아요감소 {

        @Test
        void 존재하는_상품이면_decrementLikeCount가_호출된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.incrementLikeCount();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // act
            productService.decrementLikeCount(1L);

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }
}
