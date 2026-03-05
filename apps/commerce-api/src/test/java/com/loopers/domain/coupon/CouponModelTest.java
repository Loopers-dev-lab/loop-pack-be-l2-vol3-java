package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    @DisplayName("정액 쿠폰은 고정 할인 금액을 계산한다.")
    @Test
    void calculatesFixedDiscount() {
        CouponModel coupon = new CouponModel(
            "신규가입 3천원",
            CouponType.FIXED,
            3000L,
            null,
            ZonedDateTime.now().plusDays(1)
        );

        long discount = coupon.calculateDiscount(10000L);

        assertThat(discount).isEqualTo(3000L);
    }

    @DisplayName("정률 쿠폰은 비율 할인 금액을 계산한다.")
    @Test
    void calculatesRateDiscount() {
        CouponModel coupon = new CouponModel(
            "10퍼 할인",
            CouponType.RATE,
            10L,
            null,
            ZonedDateTime.now().plusDays(1)
        );

        long discount = coupon.calculateDiscount(50000L);

        assertThat(discount).isEqualTo(5000L);
    }

    @DisplayName("최소 주문 금액 미충족이면 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenMinOrderAmountIsNotMet() {
        CouponModel coupon = new CouponModel(
            "1만원 이상 할인",
            CouponType.FIXED,
            2000L,
            10000L,
            ZonedDateTime.now().plusDays(1)
        );

        CoreException result = assertThrows(CoreException.class, () -> coupon.calculateDiscount(9000L));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
