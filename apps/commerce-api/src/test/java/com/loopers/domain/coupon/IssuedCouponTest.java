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
            IssuedCoupon coupon = IssuedCoupon.create(1L, 100L);

            // assert
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        }
    }

    @DisplayName("사용할 때,")
    @Nested
    class 사용 {

        @Test
        void ISSUED가_아니면_예외가_발생한다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.create(1L, 100L);
            coupon.use(1L);

            // act & assert — 이미 USED 상태
            assertThatThrownBy(() -> coupon.use(2L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.INVALID_COUPON_STATUS);
        }

        @Test
        void ISSUED이면_USED로_전이되고_orderId가_설정된다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.create(1L, 100L);

            // act
            coupon.use(50L);

            // assert
            assertThat(coupon)
                    .extracting(IssuedCoupon::getStatus, IssuedCoupon::getOrderId)
                    .containsExactly(IssuedCouponStatus.USED, 50L);
        }
    }

    @DisplayName("만료할 때,")
    @Nested
    class 만료 {

        @Test
        void 만료_시_EXPIRED로_전이된다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.create(1L, 100L);

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
            IssuedCoupon coupon = IssuedCoupon.create(1L, 100L);

            // act & assert
            assertThatThrownBy(() -> coupon.validateOwnership(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.NOT_OWNER);
        }
    }
}
