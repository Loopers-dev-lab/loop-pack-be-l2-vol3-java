package com.loopers.domain.coupon;

import com.loopers.support.enums.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponModel 단위 테스트")
class CouponModelTest {

    private CouponModel createFixedCoupon(BigDecimal value) {
        return CouponModel.create("테스트쿠폰", DiscountType.FIXED, value, null,
                LocalDateTime.now().plusDays(30));
    }

    private CouponModel createRateCoupon(BigDecimal rate) {
        return CouponModel.create("테스트쿠폰", DiscountType.RATE, rate, null,
                LocalDateTime.now().plusDays(30));
    }

    @Nested
    @DisplayName("calculateDiscount - 할인 금액 계산")
    class CalculateDiscountTests {

        @Test
        @DisplayName("FIXED 쿠폰은 discountValue를 반환한다")
        void calculateDiscount_Fixed_ShouldReturnDiscountValue() {
            CouponModel coupon = createFixedCoupon(BigDecimal.valueOf(5000));
            BigDecimal result = coupon.calculateDiscount(BigDecimal.valueOf(30000));
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("FIXED 쿠폰 할인값이 주문 금액 초과 시 주문 금액을 반환한다")
        void calculateDiscount_Fixed_WhenExceedsTotalAmount_ShouldCapAtTotalAmount() {
            CouponModel coupon = createFixedCoupon(BigDecimal.valueOf(50000));
            BigDecimal result = coupon.calculateDiscount(BigDecimal.valueOf(10000));
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("RATE 쿠폰은 비율로 할인 금액을 계산하고 FLOOR 처리한다")
        void calculateDiscount_Rate_ShouldReturnPercentageFloor() {
            CouponModel coupon = createRateCoupon(BigDecimal.valueOf(10));
            BigDecimal result = coupon.calculateDiscount(BigDecimal.valueOf(33333));
            // 33333 * 10 / 100 = 3333.3 → FLOOR → 3333
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(3333));
        }

        @Test
        @DisplayName("RATE 100% 쿠폰은 전체 금액을 반환한다")
        void calculateDiscount_Rate_100Percent_ShouldReturnTotalAmount() {
            CouponModel coupon = createRateCoupon(BigDecimal.valueOf(100));
            BigDecimal result = coupon.calculateDiscount(BigDecimal.valueOf(50000));
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }
    }

    @Nested
    @DisplayName("validateApplicable - 쿠폰 적용 가능 여부 검증")
    class ValidateApplicableTests {

        @Test
        @DisplayName("만료된 쿠폰은 COUPON_NOT_APPLICABLE 예외를 던진다")
        void validateApplicable_WhenExpired_ShouldThrowCouponNotApplicable() {
            CouponModel coupon = CouponModel.create("만료쿠폰", DiscountType.FIXED, BigDecimal.valueOf(1000), null,
                    LocalDateTime.now().minusDays(1));
            assertThatThrownBy(() -> coupon.validateApplicable(BigDecimal.valueOf(10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_APPLICABLE));
        }

        @Test
        @DisplayName("삭제된 쿠폰은 COUPON_NOT_APPLICABLE 예외를 던진다")
        void validateApplicable_WhenDeleted_ShouldThrowCouponNotApplicable() {
            CouponModel coupon = CouponModel.create("쿠폰", DiscountType.FIXED, BigDecimal.valueOf(1000), null,
                    LocalDateTime.now().plusDays(10));
            coupon.softDelete();
            assertThatThrownBy(() -> coupon.validateApplicable(BigDecimal.valueOf(10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_APPLICABLE));
        }

        @Test
        @DisplayName("최소 주문 금액 미충족 시 COUPON_NOT_APPLICABLE 예외를 던진다")
        void validateApplicable_WhenBelowMinOrderAmount_ShouldThrowCouponNotApplicable() {
            CouponModel coupon = CouponModel.create("쿠폰", DiscountType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(20000), LocalDateTime.now().plusDays(10));
            assertThatThrownBy(() -> coupon.validateApplicable(BigDecimal.valueOf(15000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_APPLICABLE));
        }

        @Test
        @DisplayName("유효한 쿠폰은 예외가 발생하지 않는다")
        void validateApplicable_WhenValid_ShouldNotThrow() {
            CouponModel coupon = CouponModel.create("쿠폰", DiscountType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(10000), LocalDateTime.now().plusDays(10));
            coupon.validateApplicable(BigDecimal.valueOf(20000)); // 예외 없음
        }
    }

    @Nested
    @DisplayName("guard - 생성 유효성 검증")
    class GuardTests {

        @Test
        @DisplayName("할인값이 0 이하이면 예외가 발생한다")
        void guard_WhenValueZeroOrNegative_ShouldThrow() {
            assertThatThrownBy(() ->
                    CouponModel.create("쿠폰", DiscountType.FIXED, BigDecimal.ZERO, null,
                            LocalDateTime.now().plusDays(10)))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("RATE 쿠폰의 할인율이 100을 초과하면 예외가 발생한다")
        void guard_WhenRateExceeds100_ShouldThrow() {
            assertThatThrownBy(() ->
                    CouponModel.create("쿠폰", DiscountType.RATE, BigDecimal.valueOf(101), null,
                            LocalDateTime.now().plusDays(10)))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("쿠폰명이 공백이면 예외가 발생한다")
        void guard_WhenNameBlank_ShouldThrow() {
            assertThatThrownBy(() ->
                    CouponModel.create("  ", DiscountType.FIXED, BigDecimal.valueOf(1000), null,
                            LocalDateTime.now().plusDays(10)))
                    .isInstanceOf(CoreException.class);
        }
    }
}
