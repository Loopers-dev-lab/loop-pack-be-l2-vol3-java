package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponDiscountPolicyTest {

    @DisplayName("정액 할인 정책을 적용할 때,")
    @Nested
    class FixedDiscountPolicyTest {

        private final CouponDiscountPolicy policy = new FixedCouponDiscountPolicy();

        @DisplayName("주문 금액이 쿠폰 금액보다 크면, 쿠폰 금액을 반환한다.")
        @Test
        void returnsCouponValue_whenOrderAmountIsGreater() {
            // arrange
            Money orderAmount = new Money(20000);
            Money couponValue = new Money(5000);

            // act
            Money discount = policy.calculateDiscount(orderAmount, couponValue, null);

            // assert
            assertThat(discount.amount()).isEqualTo(5000);
        }

        @DisplayName("주문 금액이 쿠폰 금액보다 작으면, 주문 금액을 반환한다.")
        @Test
        void returnsOrderAmount_whenOrderAmountIsLess() {
            // arrange
            Money orderAmount = new Money(3000);
            Money couponValue = new Money(5000);

            // act
            Money discount = policy.calculateDiscount(orderAmount, couponValue, null);

            // assert
            assertThat(discount.amount()).isEqualTo(3000);
        }

        @DisplayName("주문 금액과 쿠폰 금액이 같으면, 해당 금액을 반환한다.")
        @Test
        void returnsSameAmount_whenEqual() {
            // arrange
            Money orderAmount = new Money(5000);
            Money couponValue = new Money(5000);

            // act
            Money discount = policy.calculateDiscount(orderAmount, couponValue, null);

            // assert
            assertThat(discount.amount()).isEqualTo(5000);
        }
    }

    @DisplayName("정률 할인 정책을 적용할 때,")
    @Nested
    class RateDiscountPolicyTest {

        private final CouponDiscountPolicy policy = new RateCouponDiscountPolicy();

        @DisplayName("최대 할인 금액이 없으면, 비율대로 할인한다.")
        @Test
        void returnsRateDiscount_whenNoMaxLimit() {
            // arrange
            Money orderAmount = new Money(30000);
            Money rateValue = new Money(1000); // 10% = 1000

            // act
            Money discount = policy.calculateDiscount(orderAmount, rateValue, null);

            // assert
            assertThat(discount.amount()).isEqualTo(3000); // 30000 * 10%
        }

        @DisplayName("할인액이 최대 할인 금액보다 작으면, 계산된 할인액을 반환한다.")
        @Test
        void returnsCalculatedDiscount_whenBelowMaxLimit() {
            // arrange
            Money orderAmount = new Money(20000);
            Money rateValue = new Money(1000); // 10% = 1000
            Integer maxDiscountAmount = 5000;

            // act
            Money discount = policy.calculateDiscount(orderAmount, rateValue, maxDiscountAmount);

            // assert
            assertThat(discount.amount()).isEqualTo(2000); // 20000 * 10% = 2000 < 5000
        }

        @DisplayName("할인액이 최대 할인 금액보다 크면, 최대 할인 금액을 반환한다.")
        @Test
        void returnsMaxDiscount_whenAboveMaxLimit() {
            // arrange
            Money orderAmount = new Money(100000);
            Money rateValue = new Money(2000); // 20% = 2000
            Integer maxDiscountAmount = 10000;

            // act
            Money discount = policy.calculateDiscount(orderAmount, rateValue, maxDiscountAmount);

            // assert
            assertThat(discount.amount()).isEqualTo(10000); // 100000 * 20% = 20000 > 10000
        }

        @DisplayName("주문 금액이 0이면, 0을 반환한다.")
        @Test
        void returnsZero_whenOrderAmountIsZero() {
            // arrange
            Money orderAmount = Money.ZERO;
            Money rateValue = new Money(1500); // 15%

            // act
            Money discount = policy.calculateDiscount(orderAmount, rateValue, null);

            // assert
            assertThat(discount.amount()).isEqualTo(0);
        }
    }

    @DisplayName("쿠폰 타입에 따라 정책을 선택할 때,")
    @Nested
    class PolicyFactoryTest {

        @DisplayName("FIXED 타입이면, FixedCouponDiscountPolicy를 반환한다.")
        @Test
        void returnsFixedPolicy_forFixedType() {
            // act
            CouponDiscountPolicy policy = CouponDiscountPolicyFactory.getPolicy(CouponType.FIXED);

            // assert
            assertThat(policy).isInstanceOf(FixedCouponDiscountPolicy.class);
        }

        @DisplayName("RATE 타입이면, RateCouponDiscountPolicy를 반환한다.")
        @Test
        void returnsRatePolicy_forRateType() {
            // act
            CouponDiscountPolicy policy = CouponDiscountPolicyFactory.getPolicy(CouponType.RATE);

            // assert
            assertThat(policy).isInstanceOf(RateCouponDiscountPolicy.class);
        }
    }
}
