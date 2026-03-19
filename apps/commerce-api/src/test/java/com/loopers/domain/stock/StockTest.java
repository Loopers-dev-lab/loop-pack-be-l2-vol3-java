package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StockTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_재고가_생성된다() {
            Stock stock = Stock.create(1L, 100);

            assertAll(
                    () -> assertThat(stock.getProductId()).isEqualTo(1L),
                    () -> assertThat(stock.getQuantity()).isEqualTo(100),
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock.getConfirmedQuantity()).isEqualTo(0),
                    () -> assertThat(stock.getAvailableQuantity()).isEqualTo(100)
            );
        }

        @Test
        void 상품ID가_null이면_예외() {
            assertThatThrownBy(() -> Stock.create(null, 100))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 수량이_음수이면_예외() {
            assertThatThrownBy(() -> Stock.create(1L, -1))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 점유 {

        @Test
        void 가용_재고가_충분하면_점유에_성공한다() {
            Stock stock = Stock.create(1L, 100);

            stock.reserve(30);

            assertAll(
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(30),
                    () -> assertThat(stock.getAvailableQuantity()).isEqualTo(70),
                    () -> assertThat(stock.getQuantity()).isEqualTo(100)
            );
        }

        @Test
        void 가용_재고가_부족하면_예외() {
            Stock stock = Stock.create(1L, 10);

            assertThatThrownBy(() -> stock.reserve(11))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                        assertThat(ce.getMessage()).contains("재고가 부족한 상품이 있습니다");
                    });
        }

        @Test
        void 가용_재고와_동일한_수량이면_점유에_성공한다() {
            Stock stock = Stock.create(1L, 50);

            stock.reserve(50);

            assertAll(
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(50),
                    () -> assertThat(stock.getAvailableQuantity()).isEqualTo(0)
            );
        }

        @Test
        void 점유_후_총_재고는_변하지_않는다() {
            Stock stock = Stock.create(1L, 100);

            stock.reserve(30);

            assertThat(stock.getQuantity()).isEqualTo(100);
        }
    }

    @Nested
    class 확정 {

        @Test
        void 확정_시_점유_수량이_감소하고_확정_차감_수량이_증가한다() {
            Stock stock = Stock.create(1L, 100);
            stock.reserve(30);

            stock.confirm(30);

            assertAll(
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock.getConfirmedQuantity()).isEqualTo(30)
            );
        }

        @Test
        void 확정_후_가용_재고는_변하지_않는다() {
            Stock stock = Stock.create(1L, 100);
            stock.reserve(30);
            int availableBefore = stock.getAvailableQuantity();

            stock.confirm(30);

            assertThat(stock.getAvailableQuantity()).isEqualTo(availableBefore);
        }

        @Test
        void 확정_후_총_재고는_변하지_않는다() {
            Stock stock = Stock.create(1L, 100);
            stock.reserve(30);

            stock.confirm(30);

            assertThat(stock.getQuantity()).isEqualTo(100);
        }
    }

    @Nested
    class 가용_재고_계산 {

        @Test
        void 가용_재고는_총_재고에서_점유와_확정을_뺀_값이다() {
            Stock stock = Stock.create(1L, 100);
            stock.reserve(30);
            stock.confirm(10);

            // quantity=100, reserved=20, confirmed=10 → available=70
            assertThat(stock.getAvailableQuantity()).isEqualTo(70);
        }
    }
}
