package com.loopers.domain.coupon.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.coupon.CouponType;

class CouponDiscountProviderTest {

    private final CouponDiscountProvider couponDiscountProvider = new CouponDiscountProvider(
            List.of(new FixedCouponDiscountStrategy(), new RateCouponDiscountStrategy())
    );

    @DisplayName("전략을 조회할 때,")
    @Nested
    class GetStrategy {

        @DisplayName("FIXED 타입이면, FixedDiscountStrategy를 반환한다.")
        @Test
        void returnsFixedDiscountStrategy_whenTypeIsFixed() {
            // act
            var strategy = couponDiscountProvider.getStrategy(CouponType.FIXED);

            // assert
            assertThat(strategy).isInstanceOf(FixedCouponDiscountStrategy.class);
        }

        @DisplayName("RATE 타입이면, RateDiscountStrategy를 반환한다.")
        @Test
        void returnsRateDiscountStrategy_whenTypeIsRate() {
            // act
            var strategy = couponDiscountProvider.getStrategy(CouponType.RATE);

            // assert
            assertThat(strategy).isInstanceOf(RateCouponDiscountStrategy.class);
        }

        @DisplayName("지원하지 않는 타입이면, IllegalArgumentException을 던진다.")
        @Test
        void throwsException_whenTypeIsUnsupported() {
            // act & assert
            assertThatThrownBy(() -> couponDiscountProvider.getStrategy(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("지원하지 않는 쿠폰 타입입니다");
        }
    }
}
