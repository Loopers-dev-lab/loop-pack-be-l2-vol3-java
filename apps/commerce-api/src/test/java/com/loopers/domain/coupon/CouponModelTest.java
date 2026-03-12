package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    @DisplayName("쿠폰 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성할 수 있다")
        @Test
        void createsWithValidInput() {
            // given & when
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10,
                new Money(10000), LocalDateTime.of(2099, 12, 31, 23, 59));

            // then
            assertAll(
                () -> assertThat(coupon.name()).isEqualTo("10% 할인"),
                () -> assertThat(coupon.type()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.value()).isEqualTo(10),
                () -> assertThat(coupon.minOrderAmount()).isEqualTo(new Money(10000))
            );
        }

        @DisplayName("이름이 빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenNameBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("", CouponType.FIXED, 1000, null,
                    LocalDateTime.of(2099, 12, 31, 23, 59)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 0이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenValueZero() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("쿠폰", CouponType.FIXED, 0, null,
                    LocalDateTime.of(2099, 12, 31, 23, 59)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 계산")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 고정 금액을 할인한다")
        @Test
        void fixedDiscount() {
            // given
            CouponModel coupon = new CouponModel("5000원 할인", CouponType.FIXED, 5000,
                null, LocalDateTime.of(2099, 12, 31, 23, 59));

            // when
            Money discount = coupon.calculateDiscount(new Money(20000));

            // then
            assertThat(discount).isEqualTo(new Money(5000));
        }

        @DisplayName("정액 쿠폰 할인이 주문 금액보다 크면 주문 금액만큼만 할인한다")
        @Test
        void fixedDiscountCappedAtOrderAmount() {
            // given
            CouponModel coupon = new CouponModel("50000원 할인", CouponType.FIXED, 50000,
                null, LocalDateTime.of(2099, 12, 31, 23, 59));

            // when
            Money discount = coupon.calculateDiscount(new Money(20000));

            // then
            assertThat(discount).isEqualTo(new Money(20000));
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율로 할인한다")
        @Test
        void rateDiscount() {
            // given
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10,
                null, LocalDateTime.of(2099, 12, 31, 23, 59));

            // when
            Money discount = coupon.calculateDiscount(new Money(100000));

            // then
            assertThat(discount).isEqualTo(new Money(10000));
        }
    }

    @DisplayName("사용 가능 검증")
    @Nested
    class ValidateUsable {

        @DisplayName("만료된 쿠폰은 사용할 수 없다")
        @Test
        void throwsWhenExpired() {
            // given
            CouponModel coupon = new CouponModel("쿠폰", CouponType.FIXED, 1000,
                null, LocalDateTime.of(2020, 1, 1, 0, 0));

            // when & then
            CoreException result = assertThrows(CoreException.class,
                () -> coupon.validateUsable(new Money(10000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미달 시 사용할 수 없다")
        @Test
        void throwsWhenBelowMinOrderAmount() {
            // given
            CouponModel coupon = new CouponModel("쿠폰", CouponType.FIXED, 1000,
                new Money(50000), LocalDateTime.of(2099, 12, 31, 23, 59));

            // when & then
            CoreException result = assertThrows(CoreException.class,
                () -> coupon.validateUsable(new Money(10000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 쿠폰은 검증을 통과한다")
        @Test
        void passesWhenValid() {
            // given
            CouponModel coupon = new CouponModel("쿠폰", CouponType.FIXED, 1000,
                new Money(10000), LocalDateTime.of(2099, 12, 31, 23, 59));

            // when & then (예외 없이 통과)
            coupon.validateUsable(new Money(20000));
        }
    }
}
