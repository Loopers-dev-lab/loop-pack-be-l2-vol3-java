package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.support.CouponEnums;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserCoupon 모델 테스트")
class UserCouponTest {

    @Nested
    @DisplayName("발급 시")
    class Issue {

        @Test
        @DisplayName("AVAILABLE 상태로 발급된다")
        void success() {
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);

            assertThat(userCoupon.getCouponTemplateId()).isEqualTo(1L);
            assertThat(userCoupon.getMemberId()).isEqualTo(100L);
            assertThat(userCoupon.getStatus()).isEqualTo(CouponEnums.Status.AVAILABLE);
            assertThat(userCoupon.getUsedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("사용 시")
    class Use {

        @Test
        @DisplayName("USED 상태로 변경되고 usedAt이 설정된다")
        void success() {
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);

            userCoupon.use();

            assertThat(userCoupon.getStatus()).isEqualTo(CouponEnums.Status.USED);
            assertThat(userCoupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 사용된 쿠폰은 예외가 발생한다")
        void failWhenAlreadyUsed() {
            UserCoupon userCoupon = UserCoupon.reconstruct(1L, 1L, 100L, "USED", null);

            assertThatThrownBy(userCoupon::use)
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("만료된 쿠폰은 예외가 발생한다")
        void failWhenExpired() {
            UserCoupon userCoupon = UserCoupon.reconstruct(1L, 1L, 100L, "EXPIRED", null);

            assertThatThrownBy(userCoupon::use)
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("소유권 검증 시")
    class ValidateOwnership {

        @Test
        @DisplayName("본인 쿠폰이면 통과한다")
        void success() {
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);

            userCoupon.validateOwnership(100L);
            // 예외 없이 통과
        }

        @Test
        @DisplayName("타인 쿠폰이면 예외가 발생한다")
        void failWhenNotOwner() {
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);

            assertThatThrownBy(() -> userCoupon.validateOwnership(200L))
                    .isInstanceOf(CoreException.class);
        }
    }
}
