package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
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
}
