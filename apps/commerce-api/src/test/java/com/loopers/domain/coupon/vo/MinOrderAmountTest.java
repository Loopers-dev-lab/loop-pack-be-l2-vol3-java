package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MinOrderAmount VO 테스트")
class MinOrderAmountTest {

    @Nested
    @DisplayName("생성 시")
    class Create {

        @Test
        @DisplayName("0으로 생성할 수 있다")
        void successWithZero() {
            MinOrderAmount amount = new MinOrderAmount(0);
            assertThat(amount.value()).isEqualTo(0);
        }

        @Test
        @DisplayName("양수 값으로 생성할 수 있다")
        void successWithPositive() {
            MinOrderAmount amount = new MinOrderAmount(10000);
            assertThat(amount.value()).isEqualTo(10000);
        }

        @Test
        @DisplayName("음수이면 예외가 발생한다")
        void failWhenNegative() {
            assertThatThrownBy(() -> new MinOrderAmount(-1))
                    .isInstanceOf(CoreException.class);
        }
    }
}
