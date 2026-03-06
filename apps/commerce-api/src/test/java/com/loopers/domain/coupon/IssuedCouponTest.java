package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IssuedCoupon 도메인 테스트")
class IssuedCouponTest {

    private Coupon createTestCoupon() {
        return Coupon.create("테스트 쿠폰", DiscountType.FIXED, Money.of(1000L),
                Money.zero(), null, 100,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30));
    }

    private Coupon createExpiredCoupon() {
        return Coupon.create("만료 쿠폰", DiscountType.FIXED, Money.of(1000L),
                Money.zero(), null, 100,
                ZonedDateTime.now().minusDays(30), ZonedDateTime.now().minusDays(1));
    }

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("발급 쿠폰을 생성하면 AVAILABLE 상태이고 할인 정보가 스냅샷된다")
        void createIssuedCoupon() {
            Coupon coupon = createTestCoupon();
            IssuedCoupon issuedCoupon = IssuedCoupon.create(coupon, 1L);

            assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
            assertThat(issuedCoupon.getUsedOrderId()).isNull();
            assertThat(issuedCoupon.getUsedAt()).isNull();
            assertThat(issuedCoupon.getDiscountType()).isEqualTo(DiscountType.FIXED);
            assertThat(issuedCoupon.getDiscountValue()).isEqualTo(Money.of(1000L));
            assertThat(issuedCoupon.getMinOrderAmount()).isEqualTo(Money.zero());
            assertThat(issuedCoupon.getMaxDiscountAmount()).isNull();
        }

        @Test
        @DisplayName("사용자 ID가 null이면 예외가 발생한다")
        void createWithNullUserId() {
            Coupon coupon = createTestCoupon();
            assertThatThrownBy(() -> IssuedCoupon.create(coupon, null))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("쿠폰이 null이면 예외가 발생한다")
        void create_nullCoupon_throwsException() {
            assertThatThrownBy(() -> IssuedCoupon.create(null, 1L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("사용 테스트")
    class UseTest {

        @Test
        @DisplayName("AVAILABLE 상태의 쿠폰을 사용할 수 있다")
        void useSuccess() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);

            issuedCoupon.use(100L);

            assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(issuedCoupon.getUsedOrderId()).isEqualTo(100L);
            assertThat(issuedCoupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("USED 상태의 쿠폰을 사용하면 예외가 발생한다")
        void useAlreadyUsed() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);
            issuedCoupon.use(100L);

            assertThatThrownBy(() -> issuedCoupon.use(200L))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("만료된 쿠폰을 사용하면 예외가 발생한다")
        void useExpiredCoupon() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createExpiredCoupon(), 1L);

            assertThatThrownBy(() -> issuedCoupon.use(100L))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("주문 ID가 null이면 예외가 발생한다")
        void use_nullOrderId_throwsException() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);

            assertThatThrownBy(() -> issuedCoupon.use(null))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("복원 테스트")
    class RestoreTest {

        @Test
        @DisplayName("USED 상태의 쿠폰을 복원할 수 있다")
        void restoreSuccess() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);
            issuedCoupon.use(100L);

            issuedCoupon.restore();

            assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
            assertThat(issuedCoupon.getUsedOrderId()).isNull();
            assertThat(issuedCoupon.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("AVAILABLE 상태의 쿠폰을 복원하면 예외가 발생한다")
        void restoreNotUsed() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);

            assertThatThrownBy(issuedCoupon::restore)
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("소유자 검증 테스트")
    class ValidateOwnerTest {

        @Test
        @DisplayName("본인의 쿠폰이면 예외가 발생하지 않는다")
        void validateOwnerSuccess() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);
            issuedCoupon.validateOwner(1L);
        }

        @Test
        @DisplayName("타인의 쿠폰이면 예외가 발생한다")
        void validateOwnerFail() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);
            assertThatThrownBy(() -> issuedCoupon.validateOwner(2L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("할인 계산 테스트")
    class CalculateDiscountTest {

        @Test
        @DisplayName("정액 할인이 올바르게 계산된다")
        void calculateFixedDiscount() {
            IssuedCoupon issuedCoupon = IssuedCoupon.create(createTestCoupon(), 1L);

            Money discount = issuedCoupon.calculateDiscount(Money.of(10000L));

            assertThat(discount).isEqualTo(Money.of(1000L));
        }

        @Test
        @DisplayName("정률 할인이 올바르게 계산된다")
        void calculateRateDiscount() {
            Coupon rateCoupon = Coupon.create("10% 쿠폰", DiscountType.RATE, Money.of(10L),
                    Money.zero(), Money.of(5000L), 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30));
            IssuedCoupon issuedCoupon = IssuedCoupon.create(rateCoupon, 1L);

            Money discount = issuedCoupon.calculateDiscount(Money.of(30000L));

            assertThat(discount).isEqualTo(Money.of(3000L));
        }

        @Test
        @DisplayName("정률 할인 시 최대 할인 금액을 초과하지 않는다")
        void calculateRateDiscountWithMaxLimit() {
            Coupon rateCoupon = Coupon.create("50% 쿠폰", DiscountType.RATE, Money.of(50L),
                    Money.zero(), Money.of(5000L), 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30));
            IssuedCoupon issuedCoupon = IssuedCoupon.create(rateCoupon, 1L);

            Money discount = issuedCoupon.calculateDiscount(Money.of(30000L));

            assertThat(discount).isEqualTo(Money.of(5000L));
        }

        @Test
        @DisplayName("주문 금액이 부족하면 사용 검증 시 예외가 발생한다")
        void validateUsableWithInsufficientAmount() {
            Coupon coupon = Coupon.create("최소 주문 쿠폰", DiscountType.FIXED, Money.of(1000L),
                    Money.of(50000L), null, 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30));
            IssuedCoupon issuedCoupon = IssuedCoupon.create(coupon, 1L);

            assertThatThrownBy(() -> issuedCoupon.validateUsable(Money.of(30000L)))
                    .isInstanceOf(CoreException.class);
        }
    }
}
