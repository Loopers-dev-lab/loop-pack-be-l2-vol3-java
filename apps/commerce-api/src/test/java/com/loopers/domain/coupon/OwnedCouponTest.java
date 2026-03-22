package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.coupon.discount.CouponDiscountProvider;
import com.loopers.domain.coupon.discount.FixedCouponDiscountStrategy;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OwnedCouponTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);
    private static final CouponDiscountProvider DISCOUNT_PROVIDER =
            new CouponDiscountProvider(List.of(new FixedCouponDiscountStrategy()));

    @DisplayName("보유 쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 쿠폰과 사용자 ID를 입력하면, AVAILABLE 상태로 생성된다.")
        @Test
        void createsWithAvailableStatus_whenValidInput() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var userId = 1L;

            // act
            var ownedCoupon = OwnedCoupon.create(coupon, userId);

            // assert
            assertAll(
                    () -> assertThat(ownedCoupon.getCoupon()).isEqualTo(coupon),
                    () -> assertThat(ownedCoupon.getUserId()).isEqualTo(userId),
                    () -> assertThat(ownedCoupon.getStatus()).isEqualTo("AVAILABLE")
            );
        }

        @DisplayName("만료된 쿠폰을 입력하면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            var expiredCoupon = Coupon.create(new CouponTerms("만료 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            ReflectionTestUtils.setField(expiredCoupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            var userId = 1L;

            // act & assert
            assertThatThrownBy(() -> OwnedCoupon.create(expiredCoupon, userId))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.EXPIRED_COUPON);
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("본인 소유이고 유효한 쿠폰이면, 할인 금액을 반환한다.")
        @Test
        void returnsDiscountAmount_whenValid() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act
            Money discount = ownedCoupon.calculateDiscount(1L, Money.wons(20000L), DISCOUNT_PROVIDER);

            // assert
            assertThat(discount).isEqualTo(Money.wons(5000L));
        }

        @DisplayName("타인 소유의 쿠폰이면, FORBIDDEN_COUPON_ACCESS 예외가 발생한다.")
        @Test
        void throwsException_whenNotOwner() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.calculateDiscount(999L, Money.wons(20000L), DISCOUNT_PROVIDER))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.FORBIDDEN_COUPON_ACCESS);
        }

        @DisplayName("만료된 쿠폰이면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ReflectionTestUtils.setField(coupon, "expiredAt", PAST);

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.calculateDiscount(1L, Money.wons(20000L), DISCOUNT_PROVIDER))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.EXPIRED_COUPON);
        }

        @DisplayName("최소 주문 금액 미달이면, COUPON_MIN_ORDER_PRICE_NOT_MET 예외가 발생한다.")
        @Test
        void throwsException_whenBelowMinOrderPrice() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.calculateDiscount(1L, Money.wons(5000L), DISCOUNT_PROVIDER))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.COUPON_MIN_ORDER_PRICE_NOT_MET);
        }
    }

    @DisplayName("보유 쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면, USED로 변경된다.")
        @Test
        void changesStatusToUsed_whenAvailable() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act
            ownedCoupon.use();

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("USED");
        }

        @DisplayName("이미 USED 상태이면, ALREADY_USED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyUsed() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ownedCoupon.use();

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.use())
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ALREADY_USED_COUPON);
        }

        @DisplayName("EXPIRED 상태이면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ReflectionTestUtils.setField(ownedCoupon.getCoupon(), "expiredAt", PAST);

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.use())
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.EXPIRED_COUPON);
        }
    }

    @DisplayName("보유 쿠폰을 복원할 때,")
    @Nested
    class Restore {

        @DisplayName("USED 상태이면, AVAILABLE로 변경된다.")
        @Test
        void changesStatusToAvailable_whenUsed() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ownedCoupon.use();

            // act
            ownedCoupon.restore();

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("AVAILABLE");
        }

        @DisplayName("AVAILABLE 상태이면, AVAILABLE 상태가 유지된다.")
        @Test
        void keepsAvailable_whenAlreadyAvailable() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act
            ownedCoupon.restore();

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("AVAILABLE");
        }
    }

    @DisplayName("보유 쿠폰 상태를 조회할 때,")
    @Nested
    class GetStatus {

        @DisplayName("사용하지 않았고 만료되지 않았으면, AVAILABLE을 반환한다.")
        @Test
        void returnsAvailable_whenNotUsedAndNotExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("AVAILABLE");
        }

        @DisplayName("사용했으면, USED를 반환한다.")
        @Test
        void returnsUsed_whenUsed() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ownedCoupon.use();

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("USED");
        }

        @DisplayName("사용하지 않았지만 쿠폰이 만료되었으면, EXPIRED를 반환한다.")
        @Test
        void returnsExpired_whenNotUsedButCouponExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ReflectionTestUtils.setField(coupon, "expiredAt", PAST);

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("EXPIRED");
        }

        @DisplayName("사용했고 쿠폰도 만료되었으면, USED를 반환한다.")
        @Test
        void returnsUsed_whenUsedAndExpired() {
            // arrange
            var coupon = Coupon.create(new CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE));
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ownedCoupon.use();
            ReflectionTestUtils.setField(coupon, "expiredAt", PAST);

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo("USED");
        }
    }
}
