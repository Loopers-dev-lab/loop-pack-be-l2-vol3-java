package com.loopers.domain.coupon;

import com.loopers.support.enums.UserCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserCouponModel 단위 테스트")
class UserCouponModelTest {

    @Nested
    @DisplayName("markAsUsed - 쿠폰 사용 처리")
    class MarkAsUsedTests {

        @Test
        @DisplayName("AVAILABLE 상태의 쿠폰은 USED로 전이되고 usedAt, orderId가 설정된다")
        void markAsUsed_WhenAvailable_ShouldSetStatusAndTimestamp() {
            UserCouponModel userCoupon = UserCouponModel.create(1L, 10L);
            userCoupon.markAsUsed(100L);

            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(userCoupon.getUsedAt()).isNotNull();
            assertThat(userCoupon.getOrderId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("이미 USED인 쿠폰을 다시 사용하면 COUPON_NOT_AVAILABLE 예외가 발생한다")
        void markAsUsed_WhenAlreadyUsed_ShouldThrowCouponNotAvailable() {
            UserCouponModel userCoupon = UserCouponModel.create(1L, 10L);
            userCoupon.markAsUsed(100L);

            assertThatThrownBy(() -> userCoupon.markAsUsed(200L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_AVAILABLE));
        }
    }

    @Nested
    @DisplayName("isAvailable - 사용 가능 여부")
    class IsAvailableTests {

        @Test
        @DisplayName("AVAILABLE 상태이면 true를 반환한다")
        void isAvailable_WhenAvailable_ShouldReturnTrue() {
            UserCouponModel userCoupon = UserCouponModel.create(1L, 10L);
            assertThat(userCoupon.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("USED 상태이면 false를 반환한다")
        void isAvailable_WhenUsed_ShouldReturnFalse() {
            UserCouponModel userCoupon = UserCouponModel.create(1L, 10L);
            userCoupon.markAsUsed(100L);
            assertThat(userCoupon.isAvailable()).isFalse();
        }
    }
}
