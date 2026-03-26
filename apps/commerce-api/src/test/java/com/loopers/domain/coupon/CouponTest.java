package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.domain.coupon.discount.CouponDiscountProvider;
import com.loopers.domain.coupon.discount.FixedCouponDiscountStrategy;
import com.loopers.domain.coupon.discount.RateCouponDiscountStrategy;
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
            var coupon = Coupon.create(new CouponTerms("여름 할인", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));

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
            var coupon = Coupon.create(new CouponTerms("10% 할인", CouponType.RATE, 10L, 5000L, 10000L, FUTURE, 10000));

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
            var coupon = Coupon.create(new CouponTerms("쿠폰명입니다", CouponType.FIXED, 1L, null, 10000L, FUTURE, 10000));

            // assert
            assertThat(coupon.getDiscountValue()).isEqualTo(1L);
        }

        @DisplayName("정률 할인값이 경계값이면, 정상적으로 생성된다.")
        @ParameterizedTest(name = "할인값이 {0}인 정률 쿠폰")
        @ValueSource(longs = {1, 100})
        void createsRateCoupon_whenDiscountValueIsAtBoundary(long discountValue) {
            // arrange & act
            var coupon = Coupon.create(new CouponTerms("쿠폰명입니다", CouponType.RATE, discountValue, 5000L, 10000L, FUTURE, 10000));

            // assert
            assertThat(coupon.getDiscountValue()).isEqualTo(discountValue);
        }

        @DisplayName("최소 주문 금액이 0이면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsZero() {
            // arrange & act
            var coupon = Coupon.create(new CouponTerms("쿠폰명입니다", CouponType.FIXED, 5000L, null, 0L, FUTURE, 10000));

            // assert
            assertThat(coupon.getMinOrderPrice()).isEqualTo(Money.ZERO);
        }

        @DisplayName("쿠폰 유형이 null이면, REQUIRED_COUPON_TYPE 예외가 발생한다.")
        @Test
        void throwsException_whenTypeIsNull() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", null, 5000L, null, 10000L, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_COUPON_TYPE));
        }

        @DisplayName("할인값이 null이면, REQUIRED_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenDiscountValueIsNull() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, null, null, 10000L, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_DISCOUNT_VALUE));
        }

        @DisplayName("정액 할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenFixedDiscountValueIsLessThanOne() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 0L, null, 10000L, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_DISCOUNT_VALUE));
        }

        @DisplayName("정률 할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenRateDiscountValueIsLessThanOne() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.RATE, 0L, 5000L, 10000L, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_DISCOUNT_VALUE));
        }

        @DisplayName("정률 할인값이 100 초과이면, INVALID_RATE_DISCOUNT_VALUE 예외가 발생한다.")
        @Test
        void throwsException_whenRateDiscountValueIsGreaterThan100() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.RATE, 101L, 5000L, 10000L, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_RATE_DISCOUNT_VALUE));
        }

        @DisplayName("정률 쿠폰의 최대 할인 금액이 null이면, REQUIRED_MAX_DISCOUNT_AMOUNT 예외가 발생한다.")
        @Test
        void throwsException_whenRateCouponMaxDiscountAmountIsNull() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.RATE, 10L, null, 10000L, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_MAX_DISCOUNT_AMOUNT));
        }

        @DisplayName("최소 주문 금액이 null이면, REQUIRED_MIN_ORDER_PRICE 예외가 발생한다.")
        @Test
        void throwsException_whenMinOrderPriceIsNull() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, null, FUTURE, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_MIN_ORDER_PRICE));
        }

        @DisplayName("만료일이 null이면, REQUIRED_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsNull() {
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, null, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_EXPIRED_AT));
        }

        @DisplayName("만료일이 현재 시점 이전이면, INVALID_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsInThePast() {
            // arrange
            var pastDate = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, pastDate, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_EXPIRED_AT));
        }
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("발급 수량이 남아있으면, issuedCount가 1 증가한다.")
        @Test
        void incrementsIssuedCount_whenQuantityRemains() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 100));

            // act
            coupon.issue();

            // assert
            assertThat(coupon.getIssuedCount()).isEqualTo(1);
        }

        @DisplayName("발급 수량이 소진되면, COUPON_SOLD_OUT 예외가 발생한다.")
        @Test
        void throwsException_whenSoldOut() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 1));
            coupon.issue();

            // act & assert
            assertThatThrownBy(coupon::issue)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_SOLD_OUT));
        }
    }

    @DisplayName("쿠폰 만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 현재 시점 이후이면, false를 반환한다.")
        @Test
        void returnsFalse_whenNotExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));

            // act & assert
            assertThat(coupon.isExpired()).isFalse();
        }

        @DisplayName("만료일이 현재 시점 이전이면, true를 반환한다.")
        @Test
        void returnsTrue_whenExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));
            ReflectionTestUtils.setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));

            // act & assert
            assertThat(coupon.isExpired()).isTrue();
        }
    }

    @DisplayName("최소 주문 금액을 검증할 때,")
    @Nested
    class ValidateMinOrderPrice {

        @DisplayName("주문 총액이 최소 주문 금액 이상이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOrderTotalMeetsMinPrice() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));

            // act & assert
            assertThatCode(() -> coupon.validateMinOrderPrice(Money.wons(10000L)))
                    .doesNotThrowAnyException();
        }

        @DisplayName("주문 총액이 최소 주문 금액 미만이면, COUPON_MIN_ORDER_PRICE_NOT_MET 예외가 발생한다.")
        @Test
        void throwsException_whenOrderTotalIsLessThanMinPrice() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));

            // act & assert
            assertThatThrownBy(() -> coupon.validateMinOrderPrice(Money.wons(9999L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_MIN_ORDER_PRICE_NOT_MET));
        }
    }

    @DisplayName("쿠폰을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("정액(FIXED) 쿠폰의 정보를 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesFixedCoupon_whenAllValuesAreValid() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update(new ModifyCoupon(null,"수정된 쿠폰", 3000L, null, 20000L, newExpiredAt));

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
            var coupon = Coupon.create(new CouponTerms("기존 쿠폰", CouponType.RATE, 10L, 5000L, 10000L, FUTURE, 10000));
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update(new ModifyCoupon(null,"수정된 쿠폰", 20L, 8000L, 15000L, newExpiredAt));

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
            var coupon = Coupon.create(new CouponTerms("기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));
            coupon.delete();
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update(new ModifyCoupon(null,"수정된 쿠폰", 3000L, null, 20000L, newExpiredAt));

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
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));

            // act & assert
            assertThatThrownBy(() -> coupon.update(new ModifyCoupon(null,"수정 쿠폰", 0L, null, 10000L, FUTURE)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_DISCOUNT_VALUE));
        }

        @DisplayName("정률 쿠폰의 최대 할인 금액이 null이면, REQUIRED_MAX_DISCOUNT_AMOUNT 예외가 발생한다.")
        @Test
        void throwsException_whenRateCouponMaxDiscountAmountIsNull() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.RATE, 10L, 5000L, 10000L, FUTURE, 10000));

            // act & assert
            assertThatThrownBy(() -> coupon.update(new ModifyCoupon(null,"수정 쿠폰", 20L, null, 10000L, FUTURE)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_MAX_DISCOUNT_AMOUNT));
        }

        @DisplayName("만료일이 현재 시점 이전이면, INVALID_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsInThePast() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("쿠폰명", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));
            var pastDate = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> coupon.update(new ModifyCoupon(null,"수정 쿠폰", 3000L, null, 10000L, pastDate)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_EXPIRED_AT));
        }
    }

    @DisplayName("쿠폰 할인액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        private final CouponDiscountProvider couponDiscountProvider =
                new CouponDiscountProvider(java.util.List.of(new FixedCouponDiscountStrategy(), new RateCouponDiscountStrategy()));

        @DisplayName("FIXED 쿠폰이면, 할인값과 주문총액 중 작은 값을 반환한다.")
        @Test
        void returnsFixedDiscount() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("정액 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE, 10000));

            // act
            var discount = coupon.calculateDiscount(Money.wons(20000L), couponDiscountProvider);

            // assert
            assertThat(discount).isEqualTo(Money.wons(5000L));
        }

        @DisplayName("RATE 쿠폰이면, 비율 할인액과 최대할인가 중 작은 값을 반환한다.")
        @Test
        void returnsRateDiscount() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("정률 쿠폰", CouponType.RATE, 10L, 5000L, 10000L, FUTURE, 10000));

            // act
            var discount = coupon.calculateDiscount(Money.wons(30000L), couponDiscountProvider);

            // assert
            assertThat(discount).isEqualTo(Money.wons(3000L));
        }

        @DisplayName("RATE 쿠폰의 계산 할인액이 최대할인가를 초과하면, 최대할인가를 반환한다.")
        @Test
        void returnsMaxDiscountPrice_whenRateDiscountExceeds() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("정률 쿠폰", CouponType.RATE, 50L, 10000L, 10000L, FUTURE, 10000));

            // act
            var discount = coupon.calculateDiscount(Money.wons(100000L), couponDiscountProvider);

            // assert
            assertThat(discount).isEqualTo(Money.wons(10000L));
        }
    }
}
