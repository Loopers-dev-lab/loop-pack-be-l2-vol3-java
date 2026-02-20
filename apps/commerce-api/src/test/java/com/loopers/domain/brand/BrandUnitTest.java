package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandUnitTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 BrandName이면, Brand가 생성된다.")
        @Test
        void createsBrand_whenNameIsValid() {
            // arrange & act
            Brand brand = new Brand("나이키");

            // assert
            assertThat(brand.getName().value()).isEqualTo("나이키");
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Brand(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
