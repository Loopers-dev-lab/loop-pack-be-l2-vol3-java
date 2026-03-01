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

    @DisplayName("쿠폰 만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 현재 시점 이후이면, false를 반환한다.")
        @Test
        void returnsFalse_whenNotExpired() {
            // arrange
            var coupon = Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE);

            // act & assert
            assertThat(coupon.isExpired()).isFalse();
        }

        @DisplayName("만료일이 현재 시점 이전이면, true를 반환한다.")
        @Test
        void returnsTrue_whenExpired() {
            // arrange
            var coupon = Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            // 리플렉션으로 expiredAt을 과거로 변경
            try {
                var field = Coupon.class.getDeclaredField("expiredAt");
                field.setAccessible(true);
                field.set(coupon, ZonedDateTime.now().minusDays(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // act & assert
            assertThat(coupon.isExpired()).isTrue();
        }
    }

    @DisplayName("쿠폰을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("정액(FIXED) 쿠폰의 정보를 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesFixedCoupon_whenAllValuesAreValid() {
            // arrange
            var coupon = Coupon.create("기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update("수정된 쿠폰", 3000L, null, 20000L, newExpiredAt);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo(new CouponName("수정된 쿠폰")),
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(coupon.getDiscountValue()).isEqualTo(3000L),
                    () -> assertThat(coupon.getMaxDiscountPrice()).isNull(),
                    () -> assertThat(coupon.getMinOrderPrice()).isEqualTo(Money.wons(20000L)),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt)
            );
        }

        @DisplayName("정률(RATE) 쿠폰의 정보를 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesRateCoupon_whenAllValuesAreValid() {
            // arrange
            var coupon = Coupon.create("기존 쿠폰", CouponType.RATE, 10L, 5000L, 10000L, FUTURE);
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update("수정된 쿠폰", 20L, 8000L, 15000L, newExpiredAt);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo(new CouponName("수정된 쿠폰")),
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(coupon.getDiscountValue()).isEqualTo(20L),
                    () -> assertThat(coupon.getMaxDiscountPrice()).isEqualTo(Money.wons(8000L)),
                    () -> assertThat(coupon.getMinOrderPrice()).isEqualTo(Money.wons(15000L)),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt)
            );
        }

        @DisplayName("삭제된 쿠폰도 정상적으로 수정된다.")
        @Test
        void updatesDeletedCoupon_whenAllValuesAreValid() {
            // arrange
            var coupon = Coupon.create("기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            coupon.delete();
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update("수정된 쿠폰", 3000L, null, 20000L, newExpiredAt);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo(new CouponName("수정된 쿠폰")),
                    () -> assertThat(coupon.getDiscountValue()).isEqualTo(3000L),
                    () -> assertThat(coupon.getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenDiscountValueIsLessThanOne() {
            // arrange
            var coupon = Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE);

            // act & assert
            assertThatThrownBy(() -> coupon.update("수정 쿠폰", 0L, null, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_DISCOUNT_VALUE));
        }

        @DisplayName("정률 쿠폰의 최대 할인 금액이 null이면, REQUIRED_MAX_DISCOUNT_AMOUNT 예외가 발생한다.")
        @Test
        void throwsException_whenRateCouponMaxDiscountAmountIsNull() {
            // arrange
            var coupon = Coupon.create("쿠폰명", CouponType.RATE, 10L, 5000L, 10000L, FUTURE);

            // act & assert
            assertThatThrownBy(() -> coupon.update("수정 쿠폰", 20L, null, 10000L, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_MAX_DISCOUNT_AMOUNT));
        }

        @DisplayName("만료일이 현재 시점 이전이면, INVALID_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsInThePast() {
            // arrange
            var coupon = Coupon.create("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var pastDate = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> coupon.update("수정 쿠폰", 3000L, null, 10000L, pastDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_EXPIRED_AT));
        }
    }
}
