package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {

    @DisplayName("Quantity를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("1 이상이면, 정상 생성된다.")
        @Test
        void createsQuantity_whenValueIsPositive() {
            Quantity quantity = new Quantity(5);
            assertThat(quantity.value()).isEqualTo(5);
        }

        @DisplayName("0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZero() {
            CoreException result = assertThrows(CoreException.class, () -> new Quantity(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            CoreException result = assertThrows(CoreException.class, () -> new Quantity(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("수량을 합산할 때, ")
    @Nested
    class Add {

        @DisplayName("양수를 합산하면, 값이 증가한다.")
        @Test
        void addsQuantity_whenAmountIsPositive() {
            Quantity quantity = new Quantity(3);
            Quantity result = quantity.add(2);
            assertThat(result.value()).isEqualTo(5);
        }

        @DisplayName("0을 합산하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsZero() {
            Quantity quantity = new Quantity(3);
            CoreException result = assertThrows(CoreException.class, () -> quantity.add(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("음수를 합산하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsNegative() {
            Quantity quantity = new Quantity(3);
            CoreException result = assertThrows(CoreException.class, () -> quantity.add(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
