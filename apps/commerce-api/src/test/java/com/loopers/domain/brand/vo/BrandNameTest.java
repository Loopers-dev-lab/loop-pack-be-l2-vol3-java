package com.loopers.domain.brand.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BrandNameTest {

    @DisplayName("BrandName을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상 생성된다")
        @Test
        void success() {
            BrandName brandName = assertDoesNotThrow(() -> new BrandName("나이키"));
            assertThat(brandName.value()).isEqualTo("나이키");
        }

        @DisplayName("null이면 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new BrandName(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("빈 값이면 예외가 발생한다")
        @Test
        void throwsException_whenEmpty() {
            assertThatThrownBy(() -> new BrandName(""))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("공백만 있으면 예외가 발생한다")
        @Test
        void throwsException_whenBlank() {
            assertThatThrownBy(() -> new BrandName("   "))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
