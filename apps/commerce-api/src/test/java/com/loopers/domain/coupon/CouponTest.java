package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CouponTest {

    @DisplayName("쿠폰 생성 시,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenFieldsAreValid() {
            // act
            Coupon coupon = Coupon.create("신규 회원 쿠폰", Coupon.DiscountType.FIXED, 1000L, 1000L, LocalDateTime.now().plusDays(30));

            // assert
            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규 회원 쿠폰"),
                () -> assertThat(coupon.getDiscountType()).isEqualTo(Coupon.DiscountType.FIXED),
                () -> assertThat(coupon.getDiscountValue()).isEqualTo(1000L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(1000L)
            );
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.create(null, Coupon.DiscountType.FIXED, 1000L, 1000L, LocalDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.create("  ", Coupon.DiscountType.FIXED, 1000L, 1000L, LocalDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FIXED 타입의 discountValue가 minOrderAmount를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenFixedDiscountValueExceedsMinOrderAmount() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", Coupon.DiscountType.FIXED, 5000L, 3000L, LocalDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 타입의 discountValue가 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateDiscountValueExceeds100() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", Coupon.DiscountType.RATE, 101L, 0L, LocalDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("discountValue가 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountValueIsZeroOrNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", Coupon.DiscountType.FIXED, 0L, 0L, LocalDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("expiresAt이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiresAtIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", Coupon.DiscountType.FIXED, 1000L, 1000L, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액 계산 시,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 할인(FIXED)은 할인 금액을 그대로 반환한다.")
        @Test
        void returnsDiscountValue_whenFixed() {
            // arrange
            long discountAmount = 1000L;
            long orderAmount = 50000L;
            Coupon coupon = Coupon.create("정액 할인 쿠폰", Coupon.DiscountType.FIXED, discountAmount, discountAmount, LocalDateTime.now().plusDays(30));

            // act
            long result = coupon.calculateDiscount(orderAmount);

            // assert
            assertThat(result).isEqualTo(discountAmount);
        }

        @DisplayName("정률 할인(RATE)은 소수점 이하를 버림하여 반환한다.")
        @Test
        void returnsFlooredDiscount_whenRate() {
            // arrange
            long ratePercent = 10L;
            long orderAmount = 30001L; // 30001 * 10% = 3000.1 → 버림 → 3000
            Coupon coupon = Coupon.create("정률 할인 쿠폰", Coupon.DiscountType.RATE, ratePercent, 0L, LocalDateTime.now().plusDays(30));

            // act
            long result = coupon.calculateDiscount(orderAmount);

            // assert
            assertThat(result).isEqualTo(3000L);
        }

        @DisplayName("주문 금액이 최소 주문 금액보다 적으면 MIN_ORDER_AMOUNT_NOT_MET 예외가 발생한다.")
        @Test
        void throwsException_whenOrderAmountBelowMinOrderAmount() {
            // arrange
            long minOrderAmount = 50000L;
            long orderAmount = minOrderAmount - 1;
            Coupon coupon = Coupon.create("5만원 이상 쿠폰", Coupon.DiscountType.FIXED, 1000L, minOrderAmount, LocalDateTime.now().plusDays(30));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                coupon.validateApplicable(orderAmount)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.MIN_ORDER_AMOUNT_NOT_MET);
        }

        @DisplayName("주문 금액이 최소 주문 금액과 같으면 할인 금액을 반환한다.")
        @Test
        void returnsDiscount_whenOrderAmountEqualsMinOrderAmount() {
            // arrange
            long minOrderAmount = 50000L;
            long discountAmount = 1000L;
            Coupon coupon = Coupon.create("5만원 이상 쿠폰", Coupon.DiscountType.FIXED, discountAmount, minOrderAmount, LocalDateTime.now().plusDays(30));

            // act
            long result = coupon.calculateDiscount(minOrderAmount);

            // assert
            assertThat(result).isEqualTo(discountAmount);
        }
    }
}
