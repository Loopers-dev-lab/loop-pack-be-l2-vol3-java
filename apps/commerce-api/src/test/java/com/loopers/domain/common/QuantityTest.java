package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {

    @DisplayName("Quantity를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 수량으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsQuantity_whenValueIsValid() {
            // arrange
            int value = 10;

            // act
            Quantity quantity = new Quantity(value);

            // assert
            assertThat(quantity.value()).isEqualTo(10);
        }

        @DisplayName("수량이 1이면, 정상적으로 생성된다.")
        @Test
        void createsQuantity_whenValueIsOne() {
            // arrange
            int value = 1;

            // act
            Quantity quantity = new Quantity(value);

            // assert
            assertThat(quantity.value()).isEqualTo(1);
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsZero() {
            // arrange
            int value = 0;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Quantity(value);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNegative() {
            // arrange
            int value = -1;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Quantity(value);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}