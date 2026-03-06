package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponUnitTest {

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이고 만료 전이면, USED 상태로 변경된다.")
        @Test
        void changesStatusToUsed_whenAvailableAndNotExpired() {
            // arrange
            UserCoupon coupon = new UserCoupon(1L, 1L, 30);

            // act
            coupon.use();

            // assert
            assertAll(
                () -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(coupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 USED 상태이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            // arrange
            UserCoupon coupon = new UserCoupon(1L, 1L, 30);
            coupon.use();

            // act
            CoreException result = assertThrows(CoreException.class, coupon::use);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("만료 기간이 0일이면(즉시 만료), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            // arrange
            UserCoupon coupon = new UserCoupon(1L, 1L, 0);

            // act
            CoreException result = assertThrows(CoreException.class, coupon::use);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용 가능 여부를 확인할 때,")
    @Nested
    class IsUsable {

        @DisplayName("AVAILABLE 상태이고 만료 전이면, true를 반환한다.")
        @Test
        void returnsTrue_whenAvailableAndNotExpired() {
            // arrange
            UserCoupon coupon = new UserCoupon(1L, 1L, 30);

            // act & assert
            assertThat(coupon.isUsable()).isTrue();
        }

        @DisplayName("USED 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenUsed() {
            // arrange
            UserCoupon coupon = new UserCoupon(1L, 1L, 30);
            coupon.use();

            // act & assert
            assertThat(coupon.isUsable()).isFalse();
        }
    }
}
