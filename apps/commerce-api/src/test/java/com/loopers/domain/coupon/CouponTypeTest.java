package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponTypeTest {

    @Nested
    class 정액_할인_계산 {

        @Test
        void 할인값이_총액보다_작으면_할인값을_반환한다() {
            BigDecimal discount = CouponType.FIXED.calculateDiscount(5000, BigDecimal.valueOf(10000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        void 할인값이_총액보다_크면_총액을_반환한다() {
            BigDecimal discount = CouponType.FIXED.calculateDiscount(5000, BigDecimal.valueOf(3000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        void 할인값과_총액이_같으면_총액을_반환한다() {
            BigDecimal discount = CouponType.FIXED.calculateDiscount(5000, BigDecimal.valueOf(5000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        void 총액이_0이면_0을_반환한다() {
            BigDecimal discount = CouponType.FIXED.calculateDiscount(5000, BigDecimal.ZERO);

            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class 정률_할인_계산 {

        @Test
        void 비율로_할인_금액을_계산한다() {
            BigDecimal discount = CouponType.RATE.calculateDiscount(10, BigDecimal.valueOf(50000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        void 소수점_이하를_버린다() {
            BigDecimal discount = CouponType.RATE.calculateDiscount(33, BigDecimal.valueOf(10000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3300));
        }

        @Test
        void 할인율이_100이면_총액_전체를_반환한다() {
            BigDecimal discount = CouponType.RATE.calculateDiscount(100, BigDecimal.valueOf(10000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        void 총액이_0이면_0을_반환한다() {
            BigDecimal discount = CouponType.RATE.calculateDiscount(10, BigDecimal.ZERO);

            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void 나누어_떨어지지_않는_금액은_버림_처리한다() {
            // 10001 * 33 / 100 = 3300.33 → 3300
            BigDecimal discount = CouponType.RATE.calculateDiscount(33, BigDecimal.valueOf(10001));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3300));
        }
    }
}
