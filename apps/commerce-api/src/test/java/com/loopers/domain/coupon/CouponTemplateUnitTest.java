package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateUnitTest {

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 할인이면, 할인 금액을 그대로 반환한다.")
        @Test
        void returnsFixedDiscount_whenTypeIsFixed() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                "1000원 할인", DiscountType.FIXED, 1000L, 5000L, null, 30
            );

            // act
            long discount = template.calculateDiscount(10000L);

            // assert
            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("정률 할인이면, 주문 금액의 비율만큼 반환한다.")
        @Test
        void returnsRateDiscount_whenTypeIsRate() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                "10% 할인", DiscountType.RATE, 10L, 5000L, null, 30
            );

            // act
            long discount = template.calculateDiscount(20000L);

            // assert
            assertThat(discount).isEqualTo(2000L);
        }

        @DisplayName("정률 할인에 최대 할인액이 있으면, 최대 할인액을 초과하지 않는다.")
        @Test
        void capsAtMaxDiscount_whenRateExceedsMax() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                "50% 할인(최대 3000원)", DiscountType.RATE, 50L, 0L, 3000L, 30
            );

            // act
            long discount = template.calculateDiscount(20000L);

            // assert
            assertThat(discount).isEqualTo(3000L);
        }

        @DisplayName("주문 금액이 최소 주문 금액 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenOrderAmountBelowMinimum() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                "1000원 할인", DiscountType.FIXED, 1000L, 10000L, null, 30
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                template.calculateDiscount(5000L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 금액이 주문 금액을 초과하면, 주문 금액까지만 할인한다.")
        @Test
        void capsAtOrderAmount_whenDiscountExceedsOrder() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                "5000원 할인", DiscountType.FIXED, 5000L, 0L, null, 30
            );

            // act
            long discount = template.calculateDiscount(3000L);

            // assert
            assertThat(discount).isEqualTo(3000L);
        }
    }
}
