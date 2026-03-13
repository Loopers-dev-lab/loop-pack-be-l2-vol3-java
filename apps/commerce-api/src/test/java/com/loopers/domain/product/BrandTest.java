package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandTest {

    @DisplayName("브랜드를 생성할 때")
    @Nested
    class Create {

        @DisplayName("유효한 이름이 주어지면 성공한다")
        @Test
        void success() {
            String name = "나이키";

            Brand brand = new Brand(name);

            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name)
            );
        }

        @DisplayName("이름이 null이면 예외가 발생한다")
        @Test
        void failsWhenNameIsNull() {
            assertThatThrownBy(() -> new Brand(null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면 예외가 발생한다")
        @Test
        void failsWhenNameIsBlank() {
            assertThatThrownBy(() -> new Brand("   "))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
