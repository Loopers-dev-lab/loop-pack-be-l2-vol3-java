package com.loopers.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class MoneyTest {

    @DisplayName("Money를 생성할 때,")
    @Nested
    class Wons {

        @DisplayName("유효한 금액이면, 정상 생성된다.")
        @ParameterizedTest(name = "금액이 {0}인 경우")
        @ValueSource(longs = {0L, 10000L})
        void success(Long amount) {
            // act
            var money = Money.wons(amount);

            // assert
            assertThat(money.getAmount()).isEqualTo(amount);
        }

        @DisplayName("금액이 null이면, REQUIRED_MONEY_AMOUNT 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenNull(Long amount) {
            assertThatThrownBy(() -> Money.wons(amount))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_MONEY_AMOUNT.getMessage());
        }

        @DisplayName("금액이 음수이면, INVALID_MONEY_AMOUNT 에러가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, -999L})
        void throwsException_whenNegative(Long amount) {
            assertThatThrownBy(() -> Money.wons(amount))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_MONEY_AMOUNT.getMessage());
        }
    }
}