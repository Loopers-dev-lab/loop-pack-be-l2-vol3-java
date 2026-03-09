package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponName VO 테스트")
class CouponNameTest {

    @Nested
    @DisplayName("생성 시")
    class Create {

        @Test
        @DisplayName("정상적인 이름으로 생성할 수 있다")
        void success() {
            CouponName couponName = new CouponName("신규가입 할인");
            assertThat(couponName.value()).isEqualTo("신규가입 할인");
        }

        @Test
        @DisplayName("null이면 예외가 발생한다")
        void failWhenNull() {
            assertThatThrownBy(() -> new CouponName(null))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("빈 문자열이면 예외가 발생한다")
        void failWhenBlank() {
            assertThatThrownBy(() -> new CouponName("  "))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("50자를 초과하면 예외가 발생한다")
        void failWhenExceedsMaxLength() {
            String longName = "a".repeat(51);
            assertThatThrownBy(() -> new CouponName(longName))
                    .isInstanceOf(CoreException.class);
        }
    }
}
