package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class ProductNameTest {

    @DisplayName("ProductName을 생성할 때,")
    @Nested
    class Constructor {

        @DisplayName("유효한 이름이면, 정상 생성된다.")
        @ParameterizedTest(name = "길이가 {0}인 이름")
        @ValueSource(ints = {2, 50, 100})
        void success(int length) {
            // arrange
            var value = "A".repeat(length);

            // act
            var productName = new ProductName(value);

            // assert
            assertThat(productName.getValue()).isEqualTo(value);
        }

        @DisplayName("이름이 null이면, REQUIRED_PRODUCT_NAME 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenNull(String value) {
            assertThatThrownBy(() -> new ProductName(value))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_PRODUCT_NAME.getMessage());
        }

        @DisplayName("이름이 2자 미만이면, INVALID_PRODUCT_NAME 에러가 발생한다.")
        @Test
        void throwsException_whenTooShort() {
            assertThatThrownBy(() -> new ProductName("A"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_PRODUCT_NAME.getMessage());
        }

        @DisplayName("이름이 100자 초과이면, INVALID_PRODUCT_NAME 에러가 발생한다.")
        @Test
        void throwsException_whenTooLong() {
            assertThatThrownBy(() -> new ProductName("A".repeat(101)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_PRODUCT_NAME.getMessage());
        }
    }
}