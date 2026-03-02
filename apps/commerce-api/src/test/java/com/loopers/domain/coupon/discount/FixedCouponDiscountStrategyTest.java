package com.loopers.domain.coupon.discount;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.shared.Money;

class FixedCouponDiscountStrategyTest {

    private final CouponDiscountStrategy strategy = new FixedCouponDiscountStrategy();

    @DisplayName("할인액을 계산할 때,")
    @Nested
    class Calculate {

        @DisplayName("할인값이 주문총액보다 작으면, 할인값을 반환한다.")
        @Test
        void returnsDiscountValue_whenLessThanOrderTotal() {
            // arrange
            var orderTotal = Money.wons(10000L);

            // act
            var discount = strategy.calculate(3000L, orderTotal, null);

            // assert
            assertThat(discount).isEqualTo(Money.wons(3000L));
        }

        @DisplayName("할인값이 주문총액보다 크면, 주문총액을 반환한다.")
        @Test
        void returnsOrderTotal_whenDiscountValueExceedsOrderTotal() {
            // arrange
            var orderTotal = Money.wons(3000L);

            // act
            var discount = strategy.calculate(5000L, orderTotal, null);

            // assert
            assertThat(discount).isEqualTo(Money.wons(3000L));
        }
    }
}
