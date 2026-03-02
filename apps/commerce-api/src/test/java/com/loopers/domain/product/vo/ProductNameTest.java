package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ProductNameTest {

    @DisplayName("ProductName을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상 생성된다")
        @Test
        void success() {
            ProductName productName = assertDoesNotThrow(() -> new ProductName("나이키 에어맥스"));
            assertThat(productName.value()).isEqualTo("나이키 에어맥스");
        }

        @DisplayName("null이면 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new ProductName(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("빈 값이면 예외가 발생한다")
        @Test
        void throwsException_whenEmpty() {
            assertThatThrownBy(() -> new ProductName(""))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("공백만 있으면 예외가 발생한다")
        @Test
        void throwsException_whenBlank() {
            assertThatThrownBy(() -> new ProductName("   "))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
