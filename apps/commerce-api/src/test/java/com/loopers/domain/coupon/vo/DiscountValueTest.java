package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DiscountValue VO 테스트")
class DiscountValueTest {

    @Nested
    @DisplayName("생성 시")
    class Create {

        @Test
        @DisplayName("양수 값으로 생성할 수 있다")
        void success() {
            DiscountValue discountValue = new DiscountValue(1000);
            assertThat(discountValue.value()).isEqualTo(1000);
        }

        @Test
        @DisplayName("0이면 예외가 발생한다")
        void failWhenZero() {
            assertThatThrownBy(() -> new DiscountValue(0))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("음수이면 예외가 발생한다")
        void failWhenNegative() {
            assertThatThrownBy(() -> new DiscountValue(-1))
                    .isInstanceOf(CoreException.class);
        }
    }
}
