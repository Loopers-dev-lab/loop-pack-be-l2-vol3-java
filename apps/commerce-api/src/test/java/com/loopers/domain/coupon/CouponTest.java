package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    private ZonedDateTime futureDate() {
        return ZonedDateTime.now().plusDays(30);
    }

    private ZonedDateTime pastDate() {
        return ZonedDateTime.now().minusDays(1);
    }

    @DisplayName("Coupon을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, Coupon이 생성된다.")
        @Test
        void createsCoupon_whenValidInfo() {
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, futureDate());

            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("10% 할인"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(10),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(10000)
            );
        }

        @DisplayName("이름이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsBlank() {
            assertThrows(IllegalArgumentException.class,
                () -> new Coupon("", CouponType.FIXED, 1000, 0, futureDate()));
        }

        @DisplayName("타입이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenTypeIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> new Coupon("할인쿠폰", null, 1000, 0, futureDate()));
        }

        @DisplayName("값이 0이면, 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsZero() {
            assertThrows(IllegalStateException.class,
                () -> new Coupon("할인쿠폰", CouponType.FIXED, 0, 0, futureDate()));
        }

        @DisplayName("만료일이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> new Coupon("할인쿠폰", CouponType.FIXED, 1000, 0, null));
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입이면, 쿠폰 값과 주문 금액 중 작은 값을 반환한다.")
        @Test
        void returnsFixedDiscount_whenTypeIsFixed() {
            Coupon coupon = new Coupon("5000원 할인", CouponType.FIXED, 5000, 0, futureDate());

            Money discount = coupon.calculateDiscount(new Money(10000));

            assertThat(discount).isEqualTo(new Money(5000));
        }

        @DisplayName("FIXED 타입이고 주문 금액보다 크면, 주문 금액을 반환한다.")
        @Test
        void returnsOrderAmount_whenFixedExceedsOrderAmount() {
            Coupon coupon = new Coupon("5000원 할인", CouponType.FIXED, 5000, 0, futureDate());

            Money discount = coupon.calculateDiscount(new Money(3000));

            assertThat(discount).isEqualTo(new Money(3000));
        }

        @DisplayName("RATE 타입이면, 주문 금액의 비율을 반환한다.")
        @Test
        void returnsRateDiscount_whenTypeIsRate() {
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 0, futureDate());

            Money discount = coupon.calculateDiscount(new Money(20000));

            assertThat(discount).isEqualTo(new Money(2000));
        }
    }

    @DisplayName("만료 여부를 확인할 때, ")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 지났으면, true를 반환한다.")
        @Test
        void returnsTrue_whenExpired() {
            Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 1000, 0, pastDate());

            assertThat(coupon.isExpired()).isTrue();
        }

        @DisplayName("만료일이 지나지 않았으면, false를 반환한다.")
        @Test
        void returnsFalse_whenNotExpired() {
            Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 1000, 0, futureDate());

            assertThat(coupon.isExpired()).isFalse();
        }
    }

    @DisplayName("적용 가능 여부를 검증할 때, ")
    @Nested
    class ValidateApplicable {

        @DisplayName("만료된 쿠폰이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExpired() {
            Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 1000, 0, pastDate());

            assertThrows(Exception.class,
                () -> coupon.validateApplicable(new Money(10000)));
        }

        @DisplayName("최소 주문 금액 미달이면, 예외가 발생한다.")
        @Test
        void throwsException_whenBelowMinOrderAmount() {
            Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 1000, 10000, futureDate());

            assertThrows(Exception.class,
                () -> coupon.validateApplicable(new Money(5000)));
        }

        @DisplayName("조건을 만족하면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenConditionsMet() {
            Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 1000, 10000, futureDate());

            coupon.validateApplicable(new Money(10000));
        }
    }

    @DisplayName("쿠폰 정보를 변경할 때, ")
    @Nested
    class ChangeDetails {

        @DisplayName("올바른 정보이면, 변경된다.")
        @Test
        void changesDetails_whenValidInfo() {
            Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 1000, 0, futureDate());

            coupon.changeDetails("수정된 쿠폰", CouponType.RATE, 20, 5000, futureDate());

            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(20),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(5000)
            );
        }
    }
}
