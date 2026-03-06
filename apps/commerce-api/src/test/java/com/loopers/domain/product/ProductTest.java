package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    @DisplayName("좋아요 감소 시 0 미만으로 내려가지 않는다")
    void decreaseLikeCountNotBelowZero() {
        Product product = new Product("사료", 1000, 10, "desc", 1L, 1L);

        Product decreased = product.decreaseLikeCount();

        assertThat(decreased.likeCount()).isZero();
    }

    @Test
    @DisplayName("가격이 음수면 예외가 발생한다")
    void negativePriceFails() {
        assertThatThrownBy(() -> new Product("사료", -1, 10, "desc", 1L, 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("카테고리 ID가 없으면 예외가 발생한다")
    void categoryIdMissingFails() {
        assertThatThrownBy(() -> new Product("사료", 1000, 10, "desc", null, 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("브랜드 ID가 없으면 예외가 발생한다")
    void brandIdMissingFails() {
        assertThatThrownBy(() -> new Product("사료", 1000, 10, "desc", 1L, null))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Nested
    @DisplayName("재고 차감")
    class DecreaseStock {

        @Test
        @DisplayName("정상 수량으로 재고 차감 시 재고가 줄어든다")
        void decreaseStockSuccess() {
            Product product = new Product("사료", 1000, 10, "desc", 1L, 1L);

            Product updated = product.decreaseStock(3);

            assertThat(updated.stock()).isEqualTo(7);
        }

        @Test
        @DisplayName("재고보다 많은 수량으로 차감 시 예외가 발생한다")
        void decreaseStockBelowZeroFails() {
            Product product = new Product("사료", 1000, 5, "desc", 1L, 1L);

            assertThatThrownBy(() -> product.decreaseStock(6))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("수량이 0이면 예외가 발생한다")
        void zeroQuantityFails() {
            Product product = new Product("사료", 1000, 10, "desc", 1L, 1L);

            assertThatThrownBy(() -> product.decreaseStock(0))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("재고 복원")
    class IncreaseStock {

        @Test
        @DisplayName("정상 수량으로 재고 복원 시 재고가 늘어난다")
        void increaseStockSuccess() {
            Product product = new Product("사료", 1000, 5, "desc", 1L, 1L);

            Product updated = product.increaseStock(3);

            assertThat(updated.stock()).isEqualTo(8);
        }

        @Test
        @DisplayName("수량이 0이면 예외가 발생한다")
        void zeroQuantityFails() {
            Product product = new Product("사료", 1000, 5, "desc", 1L, 1L);

            assertThatThrownBy(() -> product.increaseStock(0))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
