package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class CouponTemplateTest {

    @DisplayName("쿠폰 템플릿 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정액 쿠폰 템플릿을 생성할 수 있다")
        @Test
        void fixedCoupon() {
            CouponTemplate template = new CouponTemplate(
                "1000원 할인", CouponType.FIXED, 1000, 10000L, ZonedDateTime.now().plusDays(30)
            );

            assertAll(
                () -> assertThat(template.getName()).isEqualTo("1000원 할인"),
                () -> assertThat(template.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(template.getValue()).isEqualTo(1000),
                () -> assertThat(template.getMinOrderAmount()).isEqualTo(10000L)
            );
        }

        @DisplayName("유효한 정률 쿠폰 템플릿을 생성할 수 있다")
        @Test
        void rateCoupon() {
            CouponTemplate template = new CouponTemplate(
                "10% 할인", CouponType.RATE, 10, null, ZonedDateTime.now().plusDays(30)
            );

            assertAll(
                () -> assertThat(template.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(template.getValue()).isEqualTo(10),
                () -> assertThat(template.getMinOrderAmount()).isNull()
            );
        }

        @DisplayName("이름이 없으면 예외가 발생한다")
        @Test
        void failsWhenNameIsBlank() {
            assertThatThrownBy(() -> new CouponTemplate("", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("타입이 없으면 예외가 발생한다")
        @Test
        void failsWhenTypeIsNull() {
            assertThatThrownBy(() -> new CouponTemplate("할인", null, 1000, null, ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 0 이하이면 예외가 발생한다")
        @Test
        void failsWhenValueIsZero() {
            assertThatThrownBy(() -> new CouponTemplate("할인", CouponType.FIXED, 0, null, ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰이 100%를 초과하면 예외가 발생한다")
        @Test
        void failsWhenRateExceeds100() {
            assertThatThrownBy(() -> new CouponTemplate("할인", CouponType.RATE, 101, null, ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 없으면 예외가 발생한다")
        @Test
        void failsWhenExpiredAtIsNull() {
            assertThatThrownBy(() -> new CouponTemplate("할인", CouponType.FIXED, 1000, null, null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액 계산")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 고정 금액을 할인한다")
        @Test
        void fixedDiscount() {
            CouponTemplate template = new CouponTemplate(
                "3000원 할인", CouponType.FIXED, 3000, null, ZonedDateTime.now().plusDays(30)
            );

            assertThat(template.calculateDiscount(10_000)).isEqualTo(3_000);
        }

        @DisplayName("정액 쿠폰의 할인은 주문 금액을 초과할 수 없다")
        @Test
        void fixedDiscountCappedByOrderAmount() {
            CouponTemplate template = new CouponTemplate(
                "5000원 할인", CouponType.FIXED, 5000, null, ZonedDateTime.now().plusDays(30)
            );

            assertThat(template.calculateDiscount(3_000)).isEqualTo(3_000);
        }

        @DisplayName("정률 쿠폰은 비율만큼 할인한다")
        @Test
        void rateDiscount() {
            CouponTemplate template = new CouponTemplate(
                "10% 할인", CouponType.RATE, 10, null, ZonedDateTime.now().plusDays(30)
            );

            assertThat(template.calculateDiscount(30_000)).isEqualTo(3_000);
        }
    }

    @DisplayName("만료 확인")
    @Nested
    class Expiry {

        @DisplayName("만료일이 지났으면 true")
        @Test
        void expired() {
            CouponTemplate template = new CouponTemplate(
                "할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().minusDays(1)
            );

            assertThat(template.isExpired()).isTrue();
        }

        @DisplayName("만료일이 지나지 않았으면 false")
        @Test
        void notExpired() {
            CouponTemplate template = new CouponTemplate(
                "할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30)
            );

            assertThat(template.isExpired()).isFalse();
        }
    }

    @DisplayName("최소 주문 금액 검증")
    @Nested
    class MinOrderAmount {

        @DisplayName("주문 금액이 최소 주문 금액 미만이면 예외가 발생한다")
        @Test
        void failsWhenBelowMinimum() {
            CouponTemplate template = new CouponTemplate(
                "할인", CouponType.FIXED, 1000, 10_000L, ZonedDateTime.now().plusDays(30)
            );

            assertThatThrownBy(() -> template.validateMinOrderAmount(5_000))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.COUPON_MIN_ORDER_AMOUNT);
        }

        @DisplayName("최소 주문 금액이 없으면 어떤 금액이든 통과한다")
        @Test
        void noMinOrderAmount() {
            CouponTemplate template = new CouponTemplate(
                "할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30)
            );

            template.validateMinOrderAmount(100);
        }
    }
}
