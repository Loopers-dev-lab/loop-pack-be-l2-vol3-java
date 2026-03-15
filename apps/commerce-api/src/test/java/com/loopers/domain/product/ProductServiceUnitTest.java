package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceUnitTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @DisplayName("상품을 등록할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 정보면, 정상적으로 등록된다.")
        @Test
        void registerSuccess() {
            // given
            when(productRepository.save(any(ProductModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ProductModel result = productService.register(1L, "에어맥스", "러닝화", 129000, 100, "url");

            // then
            assertAll(
                    () -> assertThat(result.getBrandId()).isEqualTo(1L),
                    () -> assertThat(result.getName()).isEqualTo("에어맥스"),
                    () -> assertThat(result.getPrice()).isEqualTo(129000),
                    () -> assertThat(result.getStockQuantity()).isEqualTo(100)
            );
            verify(productRepository, times(1)).save(any(ProductModel.class));
        }
    }

    @DisplayName("상품을 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID면, 상품이 반환된다.")
        @Test
        void getByIdSuccess() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // when
            ProductModel result = productService.getById(1L);

            // then
            assertThat(result.getName()).isEqualTo("에어맥스");
        }

        @DisplayName("존재하지 않는 ID면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundId() {
            // given
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.getById(999L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드별 상품을 조회할 때,")
    @Nested
    class GetByBrandId {

        @DisplayName("해당 브랜드의 상품 목록이 반환된다.")
        @Test
        void getByBrandIdSuccess() {
            // given
            Sort sort = Sort.by(Sort.Direction.DESC, "id");
            List<ProductModel> products = List.of(
                    new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url"),
                    new ProductModel(1L, "에어포스", "캐주얼화", 109000, 50, "url")
            );
            when(productRepository.findAllByBrandId(1L, sort)).thenReturn(products);

            // when
            List<ProductModel> result = productService.getByBrandId(1L, sort);

            // then
            assertThat(result).hasSize(2);
        }

        @DisplayName("정렬 조건과 함께 조회하면, Sort가 리포지토리에 전달된다.")
        @Test
        void getByBrandIdWithSort() {
            // given
            Sort sort = Sort.by(Sort.Direction.DESC, "likeCount");
            List<ProductModel> products = List.of(
                    new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url"),
                    new ProductModel(1L, "에어포스", "캐주얼화", 109000, 50, "url")
            );
            when(productRepository.findAllByBrandId(1L, sort)).thenReturn(products);

            // when
            List<ProductModel> result = productService.getByBrandId(1L, sort);

            // then
            assertThat(result).hasSize(2);
            verify(productRepository).findAllByBrandId(1L, sort);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DecreaseStock {

        @DisplayName("충분한 재고가 있으면, 정상적으로 차감된다.")
        @Test
        void decreaseStockSuccess() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // when
            productService.decreaseStock(1L, 10);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(90);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithInsufficientStock() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 5, "url");
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    productService.decreaseStock(1L, 10)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
