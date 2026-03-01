package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class CouponTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정액(FIXED) 쿠폰이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsFixedCoupon_whenAllValuesAreValid() {
            // arrange & act
            var coupon = Coupon.create("여름 할인", CouponType.FIXED, 5000L, null, 10000L, FUTURE);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo(new CouponName("여름 할인")),
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(coupon.getDiscountValue()).isEqualTo(5000L),
                    () -> assertThat(coupon.getMaxDiscountPrice()).isNull(),
                    () -> assertThat(coupon.getMinOrderPrice()).isEqualTo(Money.wons(10000L)),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @DisplayName("정률(RATE) 쿠폰이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsRateCoupon_whenAllValuesAreValid() {
            // arrange & act
            var coupon = Coupon.create("10% 할인", CouponType.RATE, 10L, 5000L, 10000L, FUTURE);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo(new CouponName("10% 할인")),
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(coupon.getDiscountValue()).isEqualTo(10L),
                    () -> assertThat(coupon.getMaxDiscountPrice()).isEqualTo(Money.wons(5000L)),
                    () -> assertThat(coupon.getMinOrderPrice()).isEqualTo(Money.wons(10000L)),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @DisplayName("정액 할인값이 경계값(1)이면, 정상적으로 생성된다.")
        @Test
        void createsFixedCoupon_whenDiscountValueIsMinBoundary() {
            // arrange & act
            var coupon = Coupon.create("쿠폰명입니다", CouponType.FIXED, 1L, null, 10000L, FUTURE);

            // assert
            assertThat(coupon.getDiscountValue()).isEqualTo(1L);
        }

        @DisplayName("정률 할인값이 경계값이면, 정상적으로 생성된다.")
        @ParameterizedTest(name = "할인값이 {0}인 정률 쿠폰")
        @ValueSource(longs = {1, 100})
        void createsRateCoupon_whenDiscountValueIsAtBoundary(long discountValue) {
            // arrange & act
            var coupon = Coupon.create("쿠폰명입니다", CouponType.RATE, discountValue, 5000L, 10000L, FUTURE);

            // assert
            assertThat(coupon.getDiscountValue()).isEqualTo(discountValue);
        }

        @DisplayName("최소 주문 금액이 0이면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsZero() {
            // arrange & act
            var coupon = Coupon.create("쿠폰명입니다", CouponType.FIXED, 5000L, null, 0L, FUTURE);

            // assert
            assertThat(coupon.getMinOrderPrice()).isEqualTo(Money.ZERO);
        }

        @DisplayName("쿠폰 유형이 null이면, REQUIRED_COUPON_TYPE 예외가 발생한다.")
        @Test
        void throwsException_whenTypeIsNull() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", null, 5000L, null, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_COUPON_TYPE));
        }

        @DisplayName("할인값이 null이면, REQUIRED_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenDiscountValueIsNull() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.FIXED, null, null, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_DISCOUNT_VALUE));
        }

        @DisplayName("정액 할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenFixedDiscountValueIsLessThanOne() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.FIXED, 0L, null, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_DISCOUNT_VALUE));
        }

        @DisplayName("정률 할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenRateDiscountValueIsLessThanOne() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.RATE, 0L, 5000L, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_DISCOUNT_VALUE));
        }

        @DisplayName("정률 할인값이 100 초과이면, INVALID_RATE_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenRateDiscountValueIsGreaterThan100() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.RATE, 101L, 5000L, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_RATE_DISCOUNT_VALUE));
        }

        @DisplayName("정률 쿠폰의 최대 할인 금액이 null이면, REQUIRED_MAX_DISCOUNT_AMOUNT 예외가 발생한다.")
        @Test
        void throwsException_whenRateCouponMaxDiscountAmountIsNull() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.RATE, 10L, null, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_MAX_DISCOUNT_AMOUNT));
        }

        @DisplayName("최소 주문 금액이 null이면, REQUIRED_MIN_ORDER_PRICE 예외가 발생한다.")
        @Test
        void throwsException_whenMinOrderPriceIsNull() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, null, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_MIN_ORDER_PRICE));
        }

        @DisplayName("만료일이 null이면, REQUIRED_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsNull() {
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_EXPIRED_AT));
        }

        @DisplayName("만료일이 현재 시점 이전이면, INVALID_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsInThePast() {
            // arrange
            var pastDate = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, pastDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_EXPIRED_AT));
        }
    }
}
