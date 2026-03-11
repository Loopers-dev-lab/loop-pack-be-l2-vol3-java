package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IssuedCouponTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_ISSUED_상태로_생성된다() {
            // act
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 100L, "테스트쿠폰", DiscountType.FIXED, 1000, null);

            // assert
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        }
    }

    @DisplayName("사용 가능 검증할 때,")
    @Nested
    class 사용_가능_검증 {

        @Test
        void ISSUED가_아니면_예외가_발생한다() {
            // arrange — EXPIRED 상태로 전이
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 100L, "테스트쿠폰", DiscountType.FIXED, 1000, null);
            coupon.expire();

            // act & assert
            assertThatThrownBy(() -> coupon.validateUsable())
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.INVALID_COUPON_STATUS);
        }

        @Test
        void ISSUED이면_검증을_통과한다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 100L, "테스트쿠폰", DiscountType.FIXED, 1000, null);

            // act & assert — 예외 없이 통과
            coupon.validateUsable();
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        }
    }

    @DisplayName("만료할 때,")
    @Nested
    class 만료 {

        @Test
        void 만료_시_EXPIRED로_전이된다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 100L, "테스트쿠폰", DiscountType.FIXED, 1000, null);

            // act
            coupon.expire();

            // assert
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.EXPIRED);
        }
    }

    @DisplayName("소유권을 확인할 때,")
    @Nested
    class 소유권확인 {

        @Test
        void 본인_쿠폰이_아니면_예외가_발생한다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 100L, "테스트쿠폰", DiscountType.FIXED, 1000, null);

            // act & assert
            assertThatThrownBy(() -> coupon.validateOwnership(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.NOT_OWNER);
        }
    }
}
