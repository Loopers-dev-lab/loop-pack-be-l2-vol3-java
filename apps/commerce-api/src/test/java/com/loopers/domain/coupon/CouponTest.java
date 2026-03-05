package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();

    @Nested
    @DisplayName("할인 계산")
    class CalculateDiscount {

        @DisplayName("정액 할인: 할인 금액이 주문 금액보다 작으면 할인 금액을 반환한다")
        @Test
        void fixed_whenDiscountLessThanOrderPrice_returnsDiscountValue() {
            Coupon coupon = new Coupon("1000원 할인", DiscountType.FIXED, 1000, 0,
                NOW.plusDays(30));

            int discount = coupon.calculateDiscount(10000);

            assertThat(discount).isEqualTo(1000);
        }

        @DisplayName("정액 할인: 할인 금액이 주문 금액보다 크면 주문 금액을 반환한다")
        @Test
        void fixed_whenDiscountGreaterThanOrderPrice_returnsOrderPrice() {
            Coupon coupon = new Coupon("10000원 할인", DiscountType.FIXED, 10000, 0,
                NOW.plusDays(30));

            int discount = coupon.calculateDiscount(5000);

            assertThat(discount).isEqualTo(5000);
        }

        @DisplayName("정률 할인: 주문 금액의 비율만큼 할인한다")
        @Test
        void rate_calculatesPercentageDiscount() {
            Coupon coupon = new Coupon("10% 할인", DiscountType.RATE, 10, 0,
                NOW.plusDays(30));

            int discount = coupon.calculateDiscount(20000);

            assertThat(discount).isEqualTo(2000);
        }

        @DisplayName("정률 할인: 50% 할인을 적용한다")
        @Test
        void rate_fiftyPercentDiscount() {
            Coupon coupon = new Coupon("50% 할인", DiscountType.RATE, 50, 0,
                NOW.plusDays(30));

            int discount = coupon.calculateDiscount(30000);

            assertThat(discount).isEqualTo(15000);
        }
    }

    @Nested
    @DisplayName("사용 가능 여부 검증")
    class ValidateUsable {

        @DisplayName("만료된 쿠폰이면 예외가 발생한다")
        @Test
        void whenExpired_throwsException() {
            Coupon coupon = new Coupon("할인", DiscountType.FIXED, 1000, 0,
                NOW.minusDays(1));

            assertThatThrownBy(() -> coupon.validateUsable(10000, NOW))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미만이면 예외가 발생한다")
        @Test
        void whenBelowMinOrderAmount_throwsException() {
            Coupon coupon = new Coupon("할인", DiscountType.FIXED, 1000, 10000,
                NOW.plusDays(30));

            assertThatThrownBy(() -> coupon.validateUsable(5000, NOW))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 조건이면 정상 통과한다")
        @Test
        void whenValid_passes() {
            Coupon coupon = new Coupon("할인", DiscountType.FIXED, 1000, 10000,
                NOW.plusDays(30));

            coupon.validateUsable(15000, NOW);
        }
    }

    @Nested
    @DisplayName("쿠폰 생성 검증")
    class Creation {

        @DisplayName("할인 값이 0이면 예외가 발생한다")
        @Test
        void whenZeroDiscountValue_throwsException() {
            assertThatThrownBy(() -> new Coupon("할인", DiscountType.FIXED, 0, 0,
                NOW.plusDays(30)))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 할인이 100%를 초과하면 예외가 발생한다")
        @Test
        void whenRateExceeds100_throwsException() {
            assertThatThrownBy(() -> new Coupon("할인", DiscountType.RATE, 101, 0,
                NOW.plusDays(30)))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
