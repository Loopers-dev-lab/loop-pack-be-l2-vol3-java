package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CouponModelTest {

    private final ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정액(FIXED) 쿠폰을 정상 생성한다.")
        @Test
        void createFixedCoupon() {
            // given & when
            CouponModel coupon = new CouponModel("3000원 할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, null, expiredAt, 0);

            // then
            assertThat(coupon.getName()).isEqualTo("3000원 할인");
            assertThat(coupon.getType()).isEqualTo(CouponType.FIXED);
            assertThat(coupon.getDiscountAmount().amount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @DisplayName("정률(RATE) 쿠폰을 정상 생성한다.")
        @Test
        void createRateCoupon() {
            // given & when
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE,
                    null, 10, null, expiredAt, 0);

            // then
            assertThat(coupon.getName()).isEqualTo("10% 할인");
            assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getDiscountRate()).isEqualTo(10);
        }

        @DisplayName("이름이 없으면 예외가 발생한다.")
        @Test
        void failWithoutName() {
            assertThatThrownBy(() -> new CouponModel(null, CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, null, expiredAt, 0))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("FIXED 타입에서 discountAmount가 0이면 예외가 발생한다.")
        @Test
        void failFixedWithZeroAmount() {
            assertThatThrownBy(() -> new CouponModel("할인", CouponType.FIXED,
                    BigDecimal.ZERO, null, null, expiredAt, 0))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("RATE 타입에서 discountRate가 100 초과이면 예외가 발생한다.")
        @Test
        void failRateOver100() {
            assertThatThrownBy(() -> new CouponModel("할인", CouponType.RATE,
                    null, 101, null, expiredAt, 0))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 3000원 쿠폰: 주문 10000원 → 할인 3000원")
        @Test
        void fixedDiscount() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, null, expiredAt, 0);

            // when
            Money discount = coupon.calculateDiscount(Money.of(10000));

            // then
            assertThat(discount.amount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @DisplayName("정액 15000원 쿠폰: 주문 10000원 → 할인 10000원 (주문 금액 초과 불가)")
        @Test
        void fixedDiscountCapped() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.FIXED,
                    BigDecimal.valueOf(15000), null, null, expiredAt, 0);

            // when
            Money discount = coupon.calculateDiscount(Money.of(10000));

            // then
            assertThat(discount.amount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @DisplayName("정률 10% 쿠폰: 주문 30000원 → 할인 3000원")
        @Test
        void rateDiscount10() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.RATE,
                    null, 10, null, expiredAt, 0);

            // when
            Money discount = coupon.calculateDiscount(Money.of(30000));

            // then
            assertThat(discount.amount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @DisplayName("정률 50% 쿠폰: 주문 10000원 → 할인 5000원")
        @Test
        void rateDiscount50() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.RATE,
                    null, 50, null, expiredAt, 0);

            // when
            Money discount = coupon.calculateDiscount(Money.of(10000));

            // then
            assertThat(discount.amount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }
    }

    @DisplayName("최소 주문 금액을 검증할 때,")
    @Nested
    class ValidateMinOrderAmount {

        @DisplayName("조건 충족 시 통과한다.")
        @Test
        void passWhenMet() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, BigDecimal.valueOf(10000), expiredAt, 0);

            // when & then (예외 없음)
            coupon.validateMinOrderAmount(Money.of(10000));
        }

        @DisplayName("조건 미달 시 예외가 발생한다.")
        @Test
        void failWhenNotMet() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, BigDecimal.valueOf(10000), expiredAt, 0);

            // when & then
            assertThatThrownBy(() -> coupon.validateMinOrderAmount(Money.of(9999)))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("minOrderAmount가 null이면 무조건 통과한다.")
        @Test
        void passWhenNull() {
            // given
            CouponModel coupon = new CouponModel("할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, null, expiredAt, 0);

            // when & then (예외 없음)
            coupon.validateMinOrderAmount(Money.of(1));
        }
    }
}
