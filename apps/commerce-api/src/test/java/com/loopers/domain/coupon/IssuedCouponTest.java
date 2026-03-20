package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class IssuedCouponTest {

    @DisplayName("발급 쿠폰 생성")
    @Nested
    class Create {

        @DisplayName("유효한 쿠폰을 발급할 수 있다")
        @Test
        void success() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);

            assertAll(
                () -> assertThat(coupon.getCouponTemplateId()).isEqualTo(1L),
                () -> assertThat(coupon.getMemberId()).isEqualTo(100L),
                () -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(coupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("템플릿 ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenTemplateIdIsNull() {
            assertThatThrownBy(() -> new IssuedCoupon(null, 100L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("회원 ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenMemberIdIsNull() {
            assertThatThrownBy(() -> new IssuedCoupon(1L, null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰을 사용하면 USED로 변경된다")
        @Test
        void success() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);

            coupon.use();

            assertAll(
                () -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(coupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 사용된 쿠폰을 사용하면 예외가 발생한다")
        @Test
        void failsWhenAlreadyUsed() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);
            coupon.use();

            assertThatThrownBy(coupon::use)
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.COUPON_ALREADY_USED);
        }
    }

    @DisplayName("사용 가능 여부 확인")
    @Nested
    class IsUsable {

        @DisplayName("AVAILABLE이면 사용 가능하다")
        @Test
        void available() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);

            assertThat(coupon.isUsable()).isTrue();
        }

        @DisplayName("USED이면 사용 불가하다")
        @Test
        void used() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);
            coupon.use();

            assertThat(coupon.isUsable()).isFalse();
        }
    }

    @DisplayName("소유자 검증")
    @Nested
    class ValidateOwnership {

        @DisplayName("소유자가 맞으면 예외 없이 통과한다")
        @Test
        void success() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);

            coupon.validateOwnership(100L);
        }

        @DisplayName("소유자가 다르면 예외가 발생한다")
        @Test
        void failsWhenNotOwner() {
            IssuedCoupon coupon = new IssuedCoupon(1L, 100L);

            assertThatThrownBy(() -> coupon.validateOwnership(200L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.COUPON_NOT_OWNED);
        }
    }
}
