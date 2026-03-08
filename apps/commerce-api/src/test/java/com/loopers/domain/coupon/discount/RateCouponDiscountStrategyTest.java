package com.loopers.domain.coupon.discount;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.shared.Money;

class RateCouponDiscountStrategyTest {

    private final CouponDiscountStrategy strategy = new RateCouponDiscountStrategy();

    @DisplayName("할인액을 계산할 때,")
    @Nested
    class Calculate {

        @DisplayName("계산된 할인액이 최대할인가보다 작으면, 계산된 할인액을 반환한다.")
        @Test
        void returnsCalculatedDiscount_whenLessThanMaxDiscount() {
            // arrange
            var orderTotal = Money.wons(10000L);

            // act
            var discount = strategy.calculate(10L, orderTotal, Money.wons(5000L));

            // assert
            assertThat(discount).isEqualTo(Money.wons(1000L));
        }

        @DisplayName("계산된 할인액이 최대할인가를 초과하면, 최대할인가를 반환한다.")
        @Test
        void returnsMaxDiscountPrice_whenCalculatedDiscountExceeds() {
            // arrange
            var orderTotal = Money.wons(100000L);

            // act
            var discount = strategy.calculate(50L, orderTotal, Money.wons(10000L));

            // assert
            assertThat(discount).isEqualTo(Money.wons(10000L));
        }
    }
}
