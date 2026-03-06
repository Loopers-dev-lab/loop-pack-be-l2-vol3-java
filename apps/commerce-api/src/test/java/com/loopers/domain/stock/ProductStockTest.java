package com.loopers.domain.stock;

import com.loopers.domain.product.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductStockTest {

    @DisplayName("ProductStock을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, 생성된다.")
        @Test
        void createsProductStock_whenValidInfo() {
            ProductStock productStock = new ProductStock(1L, new Stock(100));

            assertAll(
                () -> assertThat(productStock.getProductId()).isEqualTo(1L),
                () -> assertThat(productStock.getStock()).isEqualTo(new Stock(100))
            );
        }

        @DisplayName("productId가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            assertThrows(NullPointerException.class,
                () -> new ProductStock(null, new Stock(100)));
        }

        @DisplayName("stock이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenStockIsNull() {
            assertThrows(NullPointerException.class,
                () -> new ProductStock(1L, null));
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class Deduct {

        @DisplayName("충분한 재고가 있으면, 재고가 차감된다.")
        @Test
        void deductsStock_whenSufficient() {
            ProductStock productStock = new ProductStock(1L, new Stock(10));

            productStock.deduct(3);

            assertThat(productStock.getStock()).isEqualTo(new Stock(7));
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInsufficient() {
            ProductStock productStock = new ProductStock(1L, new Stock(2));

            CoreException result = assertThrows(CoreException.class, () -> productStock.deduct(3));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            ProductStock productStock = new ProductStock(1L, new Stock(10));

            CoreException result = assertThrows(CoreException.class, () -> productStock.deduct(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            ProductStock productStock = new ProductStock(1L, new Stock(10));

            CoreException result = assertThrows(CoreException.class, () -> productStock.deduct(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("유효한 수량이면, 재고가 복원된다.")
        @Test
        void restoresStock_whenValidQuantity() {
            ProductStock productStock = new ProductStock(1L, new Stock(97));

            productStock.restore(3);

            assertThat(productStock.getStock()).isEqualTo(new Stock(100));
        }

        @DisplayName("복원 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            ProductStock productStock = new ProductStock(1L, new Stock(10));

            CoreException result = assertThrows(CoreException.class, () -> productStock.restore(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("복원 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            ProductStock productStock = new ProductStock(1L, new Stock(10));

            CoreException result = assertThrows(CoreException.class, () -> productStock.restore(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 변경할 때, ")
    @Nested
    class ChangeQuantity {

        @DisplayName("올바른 재고이면, 변경된다.")
        @Test
        void changesQuantity_whenValidStock() {
            ProductStock productStock = new ProductStock(1L, new Stock(100));

            productStock.changeQuantity(new Stock(50));

            assertThat(productStock.getStock()).isEqualTo(new Stock(50));
        }

        @DisplayName("stock이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenStockIsNull() {
            ProductStock productStock = new ProductStock(1L, new Stock(100));

            assertThrows(NullPointerException.class, () -> productStock.changeQuantity(null));
        }
    }
}
