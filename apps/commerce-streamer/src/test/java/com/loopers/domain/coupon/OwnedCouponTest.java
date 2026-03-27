package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnedCouponTest {

    @Mock
    private Coupon coupon;

    @DisplayName("OwnedCoupon을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 쿠폰이면, coupon과 userId가 설정된다.")
        @Test
        void setsCouponAndUserId_whenCouponIsValid() {
            // arrange
            given(coupon.isExpired()).willReturn(false);

            // act
            OwnedCoupon ownedCoupon = OwnedCoupon.create(coupon, 100L);

            // assert
            assertAll(
                    () -> assertThat(ownedCoupon.getCoupon()).isEqualTo(coupon),
                    () -> assertThat(ownedCoupon.getUserId()).isEqualTo(100L)
            );
        }

        @DisplayName("만료된 쿠폰이면, 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            given(coupon.isExpired()).willReturn(true);

            // act & assert
            assertThatThrownBy(() -> OwnedCoupon.create(coupon, 100L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
