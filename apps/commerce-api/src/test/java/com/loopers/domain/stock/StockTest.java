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
}
