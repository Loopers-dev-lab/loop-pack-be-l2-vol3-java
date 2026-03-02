package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OwnedCouponTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @DisplayName("보유 쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 쿠폰과 사용자 ID를 입력하면, AVAILABLE 상태로 생성된다.")
        @Test
        void createsWithAvailableStatus_whenValidInput() {
            // arrange
            var coupon = Coupon.create("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var userId = 1L;

            // act
            var ownedCoupon = OwnedCoupon.create(coupon, userId);

            // assert
            assertAll(
                    () -> assertThat(ownedCoupon.getCoupon()).isEqualTo(coupon),
                    () -> assertThat(ownedCoupon.getUserId()).isEqualTo(userId),
                    () -> assertThat(ownedCoupon.getStatus()).isEqualTo(OwnedCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("만료된 쿠폰을 입력하면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            var expiredCoupon = Coupon.create("만료 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            ReflectionTestUtils.setField(expiredCoupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            var userId = 1L;

            // act & assert
            assertThatThrownBy(() -> OwnedCoupon.create(expiredCoupon, userId))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.EXPIRED_COUPON);
        }
    }

    @DisplayName("보유 쿠폰 소유자를 검증할 때,")
    @Nested
    class ValidateOwner {

        @DisplayName("본인 소유의 쿠폰이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOwner() {
            // arrange
            var coupon = Coupon.create("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act & assert
            assertThatCode(() -> ownedCoupon.validateOwner(1L))
                    .doesNotThrowAnyException();
        }

        @DisplayName("타인 소유의 쿠폰이면, FORBIDDEN_COUPON_ACCESS 예외가 발생한다.")
        @Test
        void throwsException_whenNotOwner() {
            // arrange
            var coupon = Coupon.create("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.validateOwner(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.FORBIDDEN_COUPON_ACCESS);
        }
    }

    @DisplayName("보유 쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면, USED로 변경된다.")
        @Test
        void changesStatusToUsed_whenAvailable() {
            // arrange
            var coupon = Coupon.create("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);

            // act
            ownedCoupon.use();

            // assert
            assertThat(ownedCoupon.getStatus()).isEqualTo(OwnedCouponStatus.USED);
        }

        @DisplayName("이미 USED 상태이면, ALREADY_USED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyUsed() {
            // arrange
            var coupon = Coupon.create("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
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
            var coupon = Coupon.create("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE);
            var ownedCoupon = OwnedCoupon.create(coupon, 1L);
            ReflectionTestUtils.setField(ownedCoupon, "status", OwnedCouponStatus.EXPIRED);

            // act & assert
            assertThatThrownBy(() -> ownedCoupon.use())
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.EXPIRED_COUPON);
        }
    }
}
