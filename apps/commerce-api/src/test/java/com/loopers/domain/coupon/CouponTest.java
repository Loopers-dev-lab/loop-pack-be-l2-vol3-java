package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouponTest {

    private static final Long DEFAULT_USER_ID = 1L;
    private static final CouponTemplate DEFAULT_TEMPLATE = CouponTemplate.restore(
        10L, "신규 가입 쿠폰", DiscountType.FIXED, 10000, 1000, ZonedDateTime.now().plusDays(7), null, 0
    );

    private static void assertCoreException(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(CoreException.class)
            .hasMessage(message);
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class Issue {

        @Test
        @DisplayName("쿠폰 발급에 성공한다")
        void success_issue_coupon() {
            Coupon coupon = Coupon.issue(DEFAULT_USER_ID, DEFAULT_TEMPLATE);

            assertThat(coupon.getId()).isNull();
            assertThat(coupon.getRefUserId()).isEqualTo(DEFAULT_USER_ID);
            assertThat(coupon.getRefTemplateId()).isEqualTo(DEFAULT_TEMPLATE.getId());
            assertThat(coupon.getName().value()).isEqualTo(DEFAULT_TEMPLATE.getName().value());
            assertThat(coupon.getType()).isEqualTo(DEFAULT_TEMPLATE.getType());
            assertThat(coupon.getMinOrderAmount()).isEqualTo(DEFAULT_TEMPLATE.getMinOrderAmount());
            assertThat(coupon.getExpiredAt()).isEqualTo(DEFAULT_TEMPLATE.getExpiredAt());
            assertThat(coupon.getUsageType()).isEqualTo(CouponUsageType.AVAILABLE);
        }

        @DisplayName("유저 ID가 null이거나 0 이하이면, 발급시 예외를 던진다")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        void fail_when_user_id_is_invalid(Long userId) {
            assertCoreException(
                () -> Coupon.issue(userId, DEFAULT_TEMPLATE),
                ErrorMessage.Coupon.USER_ID_INVALID
            );
        }

        @Test
        @DisplayName("유저 ID가 null이면, 발급시 예외를 던진다")
        void fail_when_user_id_is_null() {
            assertCoreException(
                () -> Coupon.issue(null, DEFAULT_TEMPLATE),
                ErrorMessage.Coupon.USER_ID_INVALID
            );
        }
    }
}
