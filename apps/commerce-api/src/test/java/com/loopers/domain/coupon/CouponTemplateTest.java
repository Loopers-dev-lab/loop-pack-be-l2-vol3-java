package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CouponTemplateTest {

    private static final String DEFAULT_NAME = "신규 가입 쿠폰";
    private static final DiscountType DEFAULT_DISCOUNT_TYPE = DiscountType.FIXED;
    private static final Integer  DEFAULT_DISCOUNT_VALUE = 20;
    private static final Integer DEFAULT_MIN_ORDER_AMOUNT = 10000;
    private static final ZonedDateTime DEFAULT_EXPIRED_AT = ZonedDateTime.now().plusDays(7);

    private static void assertCoreException(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(CoreException.class)
            .hasMessage(message);
    }

    @Nested
    @DisplayName("쿠폰 템플릿 생성")
    class Create {

        @Test
        @DisplayName("쿠폰 템플릿 생성에 성공한다")
        void success_create_coupon_template() {
            CouponTemplate template = CouponTemplate.create(DEFAULT_NAME, DEFAULT_DISCOUNT_TYPE, DEFAULT_DISCOUNT_VALUE, DEFAULT_MIN_ORDER_AMOUNT, DEFAULT_EXPIRED_AT);

            assertThat(template.getId()).isNull();
            assertThat(template.getName().value()).isEqualTo(DEFAULT_NAME);
            assertThat(template.getType()).isEqualTo(DEFAULT_DISCOUNT_TYPE);
            assertThat(template.getMinOrderAmount()).isEqualTo(new Money(DEFAULT_MIN_ORDER_AMOUNT));
            assertThat(template.getExpiredAt()).isEqualTo(DEFAULT_EXPIRED_AT);
        }

        @DisplayName("쿠폰 이름이 null이거나 공백이면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void fail_when_name_is_blank(String name) {
            assertCoreException(
                () -> CouponTemplate.create(name, DEFAULT_DISCOUNT_TYPE, DEFAULT_DISCOUNT_VALUE, DEFAULT_MIN_ORDER_AMOUNT, DEFAULT_EXPIRED_AT),
                ErrorMessage.Name.NAME_REQUIRED
            );
        }

        @Test
        @DisplayName("할인 유형이 null이면, 생성시 예외를 던진다")
        void fail_when_discount_type_is_null() {
            assertCoreException(
                () -> CouponTemplate.create(DEFAULT_NAME, null, DEFAULT_DISCOUNT_VALUE, DEFAULT_MIN_ORDER_AMOUNT, DEFAULT_EXPIRED_AT),
                ErrorMessage.Coupon.DISCOUNT_TYPE_REQUIRED
            );
        }

        @DisplayName("최소 주문 금액이 null이거나 음수이면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-1, -1000})
        void fail_when_invalid_min_order_amount(Integer minOrderAmount) {
            assertCoreException(
                () -> CouponTemplate.create(DEFAULT_NAME, DEFAULT_DISCOUNT_TYPE, DEFAULT_DISCOUNT_VALUE,  minOrderAmount, DEFAULT_EXPIRED_AT),
                ErrorMessage.Money.AMOUNT_INVALID
            );
        }

        @Test
        @DisplayName("만료 일시가 null이면, 생성시 예외를 던진다")
        void fail_when_expired_at_is_null() {
            assertCoreException(
                () -> CouponTemplate.create(DEFAULT_NAME, DEFAULT_DISCOUNT_TYPE, DEFAULT_DISCOUNT_VALUE,  DEFAULT_MIN_ORDER_AMOUNT, null),
                ErrorMessage.Coupon.EXPIRED_AT_REQUIRED
            );
        }

        @Test
        @DisplayName("만료 일시가 과거이면, 생성시 예외를 던진다")
        void fail_when_expired_at_is_past() {
            ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);

            assertCoreException(
                () -> CouponTemplate.create(DEFAULT_NAME, DEFAULT_DISCOUNT_TYPE, DEFAULT_DISCOUNT_VALUE, DEFAULT_MIN_ORDER_AMOUNT, pastExpiredAt),
                ErrorMessage.Coupon.EXPIRED_AT_MUST_BE_FUTURE
            );
        }
    }
}
