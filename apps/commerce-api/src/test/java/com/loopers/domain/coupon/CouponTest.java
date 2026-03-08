package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    @DisplayName("create: 유효한 정액 쿠폰 템플릿을 생성할 수 있다")
    @Test
    void create_fixed_success() {
        ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);
        Coupon coupon = Coupon.create("신규 가입 쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), expiredAt);

        assertThat(coupon.getName()).isEqualTo("신규 가입 쿠폰");
        assertThat(coupon.getDescription()).isEqualTo("설명");
        assertThat(coupon.getType()).isEqualTo(CouponType.FIXED);
        assertThat(coupon.getDiscountValue()).isEqualByComparingTo("5000");
        assertThat(coupon.getExpiredAt()).isEqualTo(expiredAt);
    }

    @DisplayName("create: 유효한 정률 쿠폰 템플릿을 생성할 수 있다")
    @Test
    void create_rate_success() {
        Coupon coupon = Coupon.create("10% 할인 쿠폰", "설명", CouponType.RATE, BigDecimal.valueOf(10), ZonedDateTime.now().plusDays(7));

        assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
        assertThat(coupon.getDiscountValue()).isEqualByComparingTo("10");
    }

    @DisplayName("create: 이름이 비어있으면 생성에 실패한다")
    @Test
    void create_fail_blankName() {
        assertThatThrownBy(() -> Coupon.create("", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("create: 할인 값이 0 이하면 생성에 실패한다")
    @Test
    void create_fail_zeroDiscountValue() {
        assertThatThrownBy(() -> Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.ZERO, ZonedDateTime.now().plusDays(30)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("create: 정률 쿠폰의 할인율이 100%를 초과하면 생성에 실패한다")
    @Test
    void create_fail_rateExceeds100() {
        assertThatThrownBy(() -> Coupon.create("쿠폰", "설명", CouponType.RATE, BigDecimal.valueOf(101), ZonedDateTime.now().plusDays(30)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("create: 만료일이 현재 시각 이전이면 생성에 실패한다")
    @Test
    void create_fail_pastExpiredAt() {
        assertThatThrownBy(() -> Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().minusDays(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("calculateDiscount: 정액 쿠폰의 할인 금액을 계산한다")
    @Test
    void calculateDiscount_fixed() {
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(3000), ZonedDateTime.now().plusDays(30));

        BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(10000));

        assertThat(discount).isEqualByComparingTo("3000");
    }

    @DisplayName("calculateDiscount: 정률 쿠폰의 할인 금액을 계산한다")
    @Test
    void calculateDiscount_rate() {
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.RATE, BigDecimal.valueOf(10), ZonedDateTime.now().plusDays(30));

        BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(10000));

        assertThat(discount).isEqualByComparingTo("1000");
    }

    @DisplayName("calculateDiscount: 정액 할인이 주문 금액을 초과하면 주문 금액만큼만 할인된다")
    @Test
    void calculateDiscount_fixed_exceedsOrderAmount() {
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(30));

        BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(3000));

        assertThat(discount).isEqualByComparingTo("3000");
    }

    @DisplayName("update: 쿠폰 템플릿 정보를 수정할 수 있다")
    @Test
    void update_success() {
        Coupon coupon = Coupon.create("기존 쿠폰", "기존 설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(14);

        coupon.update("수정된 쿠폰", "수정된 설명", CouponType.RATE, BigDecimal.valueOf(20), newExpiredAt);

        assertThat(coupon.getName()).isEqualTo("수정된 쿠폰");
        assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
        assertThat(coupon.getDiscountValue()).isEqualByComparingTo("20");
        assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt);
    }
}
