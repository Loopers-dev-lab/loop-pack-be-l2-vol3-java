package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserCouponTest {

    private static final Long COUPON_TEMPLATE_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);

    @DisplayName("UserCoupon 생성 시")
    @Nested
    class Create {

        @DisplayName("정상적인 파라미터로 발급 쿠폰이 생성된다.")
        @Test
        void createsCoupon_whenValidParameters() {
            // act
            UserCoupon userCoupon = new UserCoupon(COUPON_TEMPLATE_ID, USER_ID, FUTURE_EXPIRED_AT);

            // assert
            assertThat(userCoupon.getCouponTemplateId()).isEqualTo(COUPON_TEMPLATE_ID);
            assertThat(userCoupon.getUserId()).isEqualTo(USER_ID);
            assertThat(userCoupon.getUsedAt()).isNull();
        }
    }

}
