package com.loopers.domain.order.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QuantityTest {

    @DisplayName("Quantity를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("1이면 정상 생성된다.")
        @Test
        void success() {
            // arrange
            int value = 1;

            // act
            Quantity quantity = assertDoesNotThrow(() -> new Quantity(value));

            // assert
            assertThat(quantity.value()).isEqualTo(1);
        }

        @DisplayName("100이면 정상 생성된다.")
        @Test
        void success_whenLargeValue() {
            // arrange
            int value = 100;

            // act
            Quantity quantity = assertDoesNotThrow(() -> new Quantity(value));

            // assert
            assertThat(quantity.value()).isEqualTo(100);
        }

        @DisplayName("0이면 CoreException(BAD_REQUEST)이 발생한다.")
        @Test
        void throwsException_whenZero() {
            // arrange
            int value = 0;

            // act & assert
            assertThatThrownBy(() -> new Quantity(value))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("음수이면 CoreException(BAD_REQUEST)이 발생한다.")
        @Test
        void throwsException_whenNegative() {
            // arrange
            int value = -1;

            // act & assert
            assertThatThrownBy(() -> new Quantity(value))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
