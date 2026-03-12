package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    @DisplayName("정액 쿠폰을 생성할 때,")
    @Nested
    class CreateFixed {

        @DisplayName("유효한 값으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsFixedCoupon_whenValuesAreValid() {
            // arrange
            String name = "신규 가입 쿠폰";
            Money value = new Money(5000);
            Money minOrderAmount = new Money(10000);
            int maxIssueCount = 100;
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            CouponTemplate coupon = CouponTemplate.createFixed(
                name, value, minOrderAmount, maxIssueCount, expiredAt
            );

            // assert
            assertThat(coupon.getName()).isEqualTo(name);
            assertThat(coupon.getType()).isEqualTo(CouponType.FIXED);
            assertThat(coupon.getValue()).isEqualTo(value);
            assertThat(coupon.getMinOrderAmount()).isEqualTo(minOrderAmount);
            assertThat(coupon.getMaxIssueCount()).isEqualTo(maxIssueCount);
            assertThat(coupon.getIssuedCount()).isEqualTo(0);
            assertThat(coupon.getExpiredAt()).isEqualTo(expiredAt);
        }

        @DisplayName("최대 발급 수가 null이면, 무제한으로 생성된다.")
        @Test
        void createsUnlimitedCoupon_whenMaxIssueCountIsNull() {
            // arrange
            String name = "무제한 쿠폰";
            Money value = new Money(3000);
            Money minOrderAmount = Money.ZERO;
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);

            // act
            CouponTemplate coupon = CouponTemplate.createFixed(
                name, value, minOrderAmount, null, expiredAt
            );

            // assert
            assertThat(coupon.getMaxIssueCount()).isNull();
            assertThat(coupon.canIssue()).isTrue();
        }
    }

    @DisplayName("정률 쿠폰을 생성할 때,")
    @Nested
    class CreateRate {

        @DisplayName("유효한 값으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsRateCoupon_whenValuesAreValid() {
            // arrange
            String name = "10% 할인 쿠폰";
            int ratePercent = 10;
            Money minOrderAmount = new Money(20000);
            Integer maxDiscountAmount = 5000;
            int maxIssueCount = 50;
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(14);

            // act
            CouponTemplate coupon = CouponTemplate.createRate(
                name, ratePercent, minOrderAmount, maxDiscountAmount, maxIssueCount, expiredAt
            );

            // assert
            assertThat(coupon.getName()).isEqualTo(name);
            assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getValue().amount()).isEqualTo(1000); // 10% = 1000 (퍼센트*100)
            assertThat(coupon.getMinOrderAmount()).isEqualTo(minOrderAmount);
            assertThat(coupon.getMaxDiscountAmount()).isEqualTo(maxDiscountAmount);
            assertThat(coupon.getMaxIssueCount()).isEqualTo(maxIssueCount);
        }

        @DisplayName("최대 할인 금액이 null이면, 무제한 할인이다.")
        @Test
        void createsUnlimitedDiscountCoupon_whenMaxDiscountIsNull() {
            // arrange
            String name = "VIP 20% 쿠폰";
            int ratePercent = 20;
            Money minOrderAmount = new Money(50000);
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            CouponTemplate coupon = CouponTemplate.createRate(
                name, ratePercent, minOrderAmount, null, 10, expiredAt
            );

            // assert
            assertThat(coupon.getMaxDiscountAmount()).isNull();
        }
    }

    @DisplayName("쿠폰 발급 가능 여부를 확인할 때,")
    @Nested
    class CanIssue {

        @DisplayName("발급 수가 최대 발급 수 미만이면, true를 반환한다.")
        @Test
        void returnsTrue_whenIssuedCountIsBelowMax() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createFixed(
                "테스트 쿠폰", new Money(1000), Money.ZERO, 10, ZonedDateTime.now().plusDays(1)
            );

            // act & assert
            assertThat(coupon.canIssue()).isTrue();
        }

        @DisplayName("발급 수가 최대 발급 수와 같으면, false를 반환한다.")
        @Test
        void returnsFalse_whenIssuedCountEqualsMax() {
            // arrange
            CouponTemplate coupon = CouponTemplate.reconstitute(
                1L, "테스트 쿠폰", CouponType.FIXED, new Money(1000),
                Money.ZERO, null, 10, 10,
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now(), null
            );

            // act & assert
            assertThat(coupon.canIssue()).isFalse();
        }

        @DisplayName("최대 발급 수가 null이면, 항상 true를 반환한다.")
        @Test
        void returnsTrue_whenMaxIssueCountIsNull() {
            // arrange
            CouponTemplate coupon = CouponTemplate.reconstitute(
                1L, "무제한 쿠폰", CouponType.FIXED, new Money(1000),
                Money.ZERO, null, null, 999999,
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now(), null
            );

            // act & assert
            assertThat(coupon.canIssue()).isTrue();
        }
    }

    @DisplayName("발급 수를 증가시킬 때,")
    @Nested
    class IncrementIssuedCount {

        @DisplayName("발급 가능하면, 발급 수가 1 증가한다.")
        @Test
        void incrementsCount_whenCanIssue() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createFixed(
                "테스트 쿠폰", new Money(1000), Money.ZERO, 10, ZonedDateTime.now().plusDays(1)
            );

            // act
            coupon.incrementIssuedCount();

            // assert
            assertThat(coupon.getIssuedCount()).isEqualTo(1);
        }

        @DisplayName("발급 불가능하면, 예외가 발생한다.")
        @Test
        void throwsException_whenCannotIssue() {
            // arrange
            CouponTemplate coupon = CouponTemplate.reconstitute(
                1L, "테스트 쿠폰", CouponType.FIXED, new Money(1000),
                Money.ZERO, null, 1, 1,
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now(), null
            );

            // act
            CoreException result = assertThrows(CoreException.class, coupon::incrementIssuedCount);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_EXHAUSTED);
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰이면, 쿠폰 금액을 반환한다.")
        @Test
        void returnsFixedAmount_forFixedCoupon() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createFixed(
                "5000원 할인", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(1)
            );
            Money orderAmount = new Money(20000);

            // act
            Money discount = coupon.calculateDiscount(orderAmount);

            // assert
            assertThat(discount.amount()).isEqualTo(5000);
        }

        @DisplayName("정액 쿠폰에서 주문금액이 쿠폰금액보다 작으면, 주문금액을 반환한다.")
        @Test
        void returnsOrderAmount_whenOrderAmountLessThanCouponValue() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createFixed(
                "5000원 할인", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(1)
            );
            Money orderAmount = new Money(3000);

            // act
            Money discount = coupon.calculateDiscount(orderAmount);

            // assert
            assertThat(discount.amount()).isEqualTo(3000);
        }

        @DisplayName("정률 쿠폰이면, 비율에 따른 금액을 반환한다.")
        @Test
        void returnsRateAmount_forRateCoupon() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createRate(
                "10% 할인", 10, Money.ZERO, null, 10, ZonedDateTime.now().plusDays(1)
            );
            Money orderAmount = new Money(30000);

            // act
            Money discount = coupon.calculateDiscount(orderAmount);

            // assert
            assertThat(discount.amount()).isEqualTo(3000); // 30000 * 10%
        }

        @DisplayName("정률 쿠폰에서 최대 할인 금액이 설정되면, 최대 금액으로 제한된다.")
        @Test
        void capsAtMaxDiscount_forRateCoupon() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createRate(
                "20% 할인 (최대 5000원)", 20, Money.ZERO, 5000, 10, ZonedDateTime.now().plusDays(1)
            );
            Money orderAmount = new Money(50000);

            // act
            Money discount = coupon.calculateDiscount(orderAmount);

            // assert
            assertThat(discount.amount()).isEqualTo(5000); // 50000 * 20% = 10000 but capped at 5000
        }
    }

    @DisplayName("삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("deletedAt이 설정된다.")
        @Test
        void setsDeletedAt() {
            // arrange
            CouponTemplate coupon = CouponTemplate.createFixed(
                "삭제 대상 쿠폰", new Money(1000), Money.ZERO, 10, ZonedDateTime.now().plusDays(1)
            );

            // act
            coupon.delete();

            // assert
            assertThat(coupon.getDeletedAt()).isNotNull();
            assertThat(coupon.isDeleted()).isTrue();
        }
    }
}
