package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelQuantityTest {

    @DisplayName("수량 제한이 없는 쿠폰은 항상 발급 가능하다")
    @Test
    void noQuantityLimitAlwaysAvailable() {
        // given
        CouponModel coupon = new CouponModel("테스트 쿠폰", CouponType.FIXED, 1000,
            new Money(10000), LocalDateTime.now().plusDays(30));

        // then
        assertAll(
            () -> assertThat(coupon.hasQuantityLimit()).isFalse(),
            () -> assertThat(coupon.isQuantityAvailable()).isTrue()
        );
    }

    @DisplayName("수량 제한이 있는 쿠폰은 발급 수량이 최대 수량 미만이면 발급 가능하다")
    @Test
    void quantityAvailableWhenUnderLimit() {
        // given
        CouponModel coupon = new CouponModel("선착순 쿠폰", CouponType.FIXED, 1000,
            new Money(10000), LocalDateTime.now().plusDays(30), 100);

        // then
        assertAll(
            () -> assertThat(coupon.hasQuantityLimit()).isTrue(),
            () -> assertThat(coupon.isQuantityAvailable()).isTrue(),
            () -> assertThat(coupon.issuedCount()).isZero()
        );
    }

    @DisplayName("incrementIssuedCount 호출 시 발급 수량이 증가한다")
    @Test
    void incrementIssuedCount() {
        // given
        CouponModel coupon = new CouponModel("선착순 쿠폰", CouponType.FIXED, 1000,
            new Money(10000), LocalDateTime.now().plusDays(30), 100);

        // when
        coupon.incrementIssuedCount();

        // then
        assertThat(coupon.issuedCount()).isEqualTo(1);
    }

    @DisplayName("발급 수량이 최대에 도달하면 예외가 발생한다")
    @Test
    void throwsWhenQuantityExceeded() {
        // given
        CouponModel coupon = new CouponModel("선착순 쿠폰", CouponType.FIXED, 1000,
            new Money(10000), LocalDateTime.now().plusDays(30), 1);
        coupon.incrementIssuedCount(); // 1/1 발급

        // when & then
        assertThrows(CoreException.class, coupon::incrementIssuedCount);
        assertThat(coupon.isQuantityAvailable()).isFalse();
    }
}
