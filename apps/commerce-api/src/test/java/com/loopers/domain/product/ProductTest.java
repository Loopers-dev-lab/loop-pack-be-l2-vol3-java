package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductTest {

    @DisplayName("상품 생성 시, ")
    @Nested
    class Create {
        @DisplayName("모든 필드 값이 유효하면 정상적으로 생성된다.")
        @Test
        void createsProduct_whenFieldsAreValid() {
            // act
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(product.getDescription()).isEqualTo("신발"),
                () -> assertThat(product.getPrice()).isEqualTo(150000),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10),
                () -> assertThat(product.getVisibility()).isEqualTo(Product.Visibility.VISIBLE)
            );
        }

        @DisplayName("description이 null이어도 정상적으로 생성된다.")
        @Test
        void createsProduct_whenDescriptionIsNull() {
            // act
            Product product = Product.create(1L, "나이키 에어맥스", null, 150000, 10);

            // assert
            assertThat(product.getDescription()).isNull();
        }

        @DisplayName("brandId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(null, "나이키 에어맥스", "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, null, "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "", "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 공백 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "   ", "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "나이키 에어맥스", "신발", null, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsZero() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "나이키 에어맥스", "신발", 0, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "나이키 에어맥스", "신발", -5, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }


        @DisplayName("stockQuantity가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockQuantityIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "나이키 에어맥스", "신발", 150000, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("stockQuantity가 0이어도 정상적으로 생성된다.")
        @Test
        void createsProduct_whenStockQuantityIsZero() {
            // act
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 0);

            // assert
            assertThat(product.getStockQuantity()).isEqualTo(0);
        }

        @DisplayName("stockQuantity가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockQuantityIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Product.create(1L, "나이키 에어맥스", "신발", 150000, -1);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    class Update {
        @DisplayName("유효한 필드로 정상적으로 수정된다.")
        @Test
        void updatesProduct_whenFieldsAreValid() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            product.update("나이키 조던", "농구화", 200000, 5);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L), // brandId 변경 불가 검증
                () -> assertThat(product.getName()).isEqualTo("나이키 조던"),
                () -> assertThat(product.getDescription()).isEqualTo("농구화"),
                () -> assertThat(product.getPrice()).isEqualTo(200000),
                () -> assertThat(product.getStockQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update(null, "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update("   ", "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsZero() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update("나이키 에어맥스", "신발", 0, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update("나이키 에어맥스", "신발", -10000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("stockQuantity가 0이면 정상적으로 수정된다.")
        @Test
        void updatesProduct_whenStockQuantityIsZero() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            product.update("나이키 에어맥스", "신발", 150000, 0);

            // assert
            assertThat(product.getStockQuantity()).isEqualTo(0);
        }

        @DisplayName("stockQuantity가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockQuantityIsNegative() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update("나이키 에어맥스", "신발", 150000, -1);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("노출 여부 변경 시, ")
    @Nested
    class ChangeVisibility {
        @DisplayName("VISIBLE에서 HIDDEN으로 변경할 수 있다.")
        @Test
        void changesVisibility_fromVisibleToHidden() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            product.changeVisibility(Product.Visibility.HIDDEN);

            // assert
            assertThat(product.getVisibility()).isEqualTo(Product.Visibility.HIDDEN);
        }

        @DisplayName("HIDDEN에서 VISIBLE로 변경할 수 있다.")
        @Test
        void changesVisibility_fromHiddenToVisible() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);
            product.changeVisibility(Product.Visibility.HIDDEN);

            // act
            product.changeVisibility(Product.Visibility.VISIBLE);

            // assert
            assertThat(product.getVisibility()).isEqualTo(Product.Visibility.VISIBLE);
        }
    }

    @DisplayName("좋아요 수 변경 시, ")
    @Nested
    class LikeCount {

        @DisplayName("기본값은 0이다.")
        @Test
        void likeCount_기본값은_0이다() {
            // act
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("incrementLikeCount 호출 시 1 증가한다.")
        @Test
        void incrementLikeCount_호출시_1증가한다() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            product.increaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("decrementLikeCount 호출 시 1 감소한다.")
        @Test
        void decrementLikeCount_호출시_1감소한다() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);
            product.increaseLikeCount();

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("decrementLikeCount를 0에서 호출해도 음수가 되지 않는다.")
        @Test
        void decrementLikeCount_0에서_호출해도_음수가_되지_않는다() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }

    @DisplayName("재고 차감 시, ")
    @Nested
    class DecreaseStock {
        @DisplayName("재고가 충분하면 정상적으로 차감된다.")
        @Test
        void decreasesStock_whenStockIsSufficient() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            product.decreaseStock(3);

            // assert
            assertThat(product.getStockQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockIsInsufficient() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 2);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(3);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsZero() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // arrange
            Product product = Product.create(1L, "나이키 에어맥스", "신발", 150000, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(-1);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
