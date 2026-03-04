package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderItemTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_주문상품이_생성된다() {
            OrderItem orderItem = OrderItem.create(1L, "운동화", new BigDecimal("50000"), 2);

            assertThat(orderItem.getProductId()).isEqualTo(1L);
            assertThat(orderItem.getProductName()).isEqualTo("운동화");
            assertThat(orderItem.getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(orderItem.getQuantity()).isEqualTo(2);
        }

        @Test
        void 상품ID가_null이면_예외() {
            assertThatThrownBy(() -> OrderItem.create(null, "운동화", new BigDecimal("50000"), 2))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("상품 ID는 필수입니다");
        }

        @Test
        void 상품명이_null이면_예외() {
            assertThatThrownBy(() -> OrderItem.create(1L, null, new BigDecimal("50000"), 2))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("상품명은 필수입니다");
        }

        @Test
        void 가격이_null이면_예외() {
            assertThatThrownBy(() -> OrderItem.create(1L, "운동화", null, 2))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("가격은 필수입니다");
        }

        @Test
        void 가격이_음수면_예외() {
            assertThatThrownBy(() -> OrderItem.create(1L, "운동화", new BigDecimal("-1"), 2))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("가격은 0 이상이어야 합니다");
        }

        @Test
        void 수량이_null이면_예외() {
            assertThatThrownBy(() -> OrderItem.create(1L, "운동화", new BigDecimal("50000"), null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("수량은 필수입니다");
        }

        @Test
        void 수량이_0이면_예외() {
            assertThatThrownBy(() -> OrderItem.create(1L, "운동화", new BigDecimal("50000"), 0))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("수량은 1 이상이어야 합니다");
        }

        @Test
        void 수량이_최대값을_초과하면_예외() {
            assertThatThrownBy(() -> OrderItem.create(1L, "운동화", new BigDecimal("50000"), 10_000_000))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("수량은 9,999,999 이하여야 합니다");
        }
    }

    @Nested
    class 주문금액_계산 {

        @Test
        void 주문금액은_가격_곱하기_수량이다() {
            OrderItem orderItem = OrderItem.create(1L, "운동화", new BigDecimal("50000"), 3);

            assertThat(orderItem.getOrderPrice()).isEqualByComparingTo(new BigDecimal("150000"));
        }

        @Test
        void 수량이_1이면_주문금액은_가격과_같다() {
            OrderItem orderItem = OrderItem.create(1L, "운동화", new BigDecimal("50000"), 1);

            assertThat(orderItem.getOrderPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }
}
