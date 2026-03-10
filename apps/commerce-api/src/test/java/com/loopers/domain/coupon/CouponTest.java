package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon 도메인 테스트")
class CouponTest {

    private Coupon createFixedCoupon(int totalQuantity) {
        return Coupon.create(
                "1000원 할인 쿠폰",
                DiscountType.FIXED,
                Money.of(1000L),
                Money.of(5000L),
                null,
                totalQuantity,
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(30)
        );
    }

    private Coupon createRateCoupon(int rate, Money maxDiscount) {
        return Coupon.create(
                rate + "% 할인 쿠폰",
                DiscountType.RATE,
                Money.of(rate),
                Money.of(10000L),
                maxDiscount,
                100,
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(30)
        );
    }

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 정액 할인 쿠폰을 생성할 수 있다")
        void createFixedCoupon() {
            Coupon coupon = Coupon.create(
                    "1000원 할인",
                    DiscountType.FIXED,
                    Money.of(1000L),
                    Money.of(5000L),
                    null,
                    100,
                    ZonedDateTime.now().minusDays(1),
                    ZonedDateTime.now().plusDays(30)
            );

            assertThat(coupon.getName()).isEqualTo("1000원 할인");
            assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.FIXED);
            assertThat(coupon.getIssuedQuantity()).isZero();
            assertThat(coupon.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("쿠폰명이 비어있으면 예외가 발생한다")
        void createWithEmptyName() {
            assertThatThrownBy(() -> Coupon.create(
                    "", DiscountType.FIXED, Money.of(1000L), null, null, 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("총 발급 수량이 0이면 예외가 발생한다")
        void createWithZeroQuantity() {
            assertThatThrownBy(() -> Coupon.create(
                    "테스트", DiscountType.FIXED, Money.of(1000L), null, null, 0,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("시작일이 종료일보다 이후면 예외가 발생한다")
        void createWithInvalidPeriod() {
            assertThatThrownBy(() -> Coupon.create(
                    "테스트", DiscountType.FIXED, Money.of(1000L), null, null, 100,
                    ZonedDateTime.now().plusDays(30), ZonedDateTime.now().minusDays(1)
            )).isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("발급 테스트")
    class IssueTest {

        @Test
        @DisplayName("수량 내에서 발급할 수 있다")
        void issueSuccess() {
            Coupon coupon = createFixedCoupon(5);

            coupon.issue();

            assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("총 수량을 초과하면 예외가 발생한다")
        void issueExceedQuantity() {
            Coupon coupon = createFixedCoupon(1);
            coupon.issue();

            assertThatThrownBy(coupon::issue).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("삭제된 쿠폰은 발급할 수 없다")
        void issue_deletedCoupon_throwsException() {
            Coupon coupon = createFixedCoupon(10);
            coupon.delete();

            assertThatThrownBy(coupon::issue).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("발급 기간이 아닌 쿠폰은 발급할 수 없다")
        void issue_outsidePeriod_throwsException() {
            Coupon coupon = Coupon.create(
                    "미래 쿠폰",
                    DiscountType.FIXED,
                    Money.of(1000L),
                    Money.of(5000L),
                    null,
                    10,
                    ZonedDateTime.now().plusDays(10),
                    ZonedDateTime.now().plusDays(30)
            );

            assertThatThrownBy(coupon::issue).isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("할인 계산 테스트")
    class CalculateDiscountTest {

        @Test
        @DisplayName("정액 할인을 계산할 수 있다")
        void calculateFixedDiscount() {
            Coupon coupon = createFixedCoupon(100);

            Money discount = coupon.calculateDiscount(Money.of(10000L));

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @Test
        @DisplayName("정액 할인이 주문금액보다 크면 주문금액만큼만 할인한다")
        void calculateFixedDiscountExceedsOrder() {
            Coupon coupon = createFixedCoupon(100);

            Money discount = coupon.calculateDiscount(Money.of(500L));

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        }

        @Test
        @DisplayName("정률 할인을 계산할 수 있다")
        void calculateRateDiscount() {
            Coupon coupon = createRateCoupon(10, null);

            Money discount = coupon.calculateDiscount(Money.of(20000L));

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }

        @Test
        @DisplayName("정률 할인에 최대 할인 금액이 적용된다")
        void calculateRateDiscountWithMaxDiscount() {
            Coupon coupon = createRateCoupon(50, Money.of(5000L));

            Money discount = coupon.calculateDiscount(Money.of(20000L));

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }
    }

    @Nested
    @DisplayName("사용 가능 검증 테스트")
    class ValidateUsableTest {

        @Test
        @DisplayName("최소 주문 금액 미달 시 예외가 발생한다")
        void validateUsableMinOrderAmount() {
            Coupon coupon = createFixedCoupon(100);

            assertThatThrownBy(() -> coupon.validateUsable(Money.of(3000L)))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("유효 기간이 만료된 쿠폰은 사용할 수 없다")
        void validateUsable_expired_throwsException() {
            Coupon coupon = Coupon.create(
                    "만료 쿠폰", DiscountType.FIXED, Money.of(1000L),
                    Money.zero(), null, 100,
                    ZonedDateTime.now().minusDays(30), ZonedDateTime.now().minusDays(1)
            );

            assertThatThrownBy(() -> coupon.validateUsable(Money.of(10000L)))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("유효 기간이 미도래한 쿠폰은 사용할 수 없다")
        void validateUsable_notYetValid_throwsException() {
            Coupon coupon = Coupon.create(
                    "미래 쿠폰", DiscountType.FIXED, Money.of(1000L),
                    Money.zero(), null, 100,
                    ZonedDateTime.now().plusDays(10), ZonedDateTime.now().plusDays(30)
            );

            assertThatThrownBy(() -> coupon.validateUsable(Money.of(10000L)))
                    .isInstanceOf(CoreException.class);
        }
    }
}
