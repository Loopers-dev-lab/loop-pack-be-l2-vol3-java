package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandNameUnitTest {

    @DisplayName("브랜드 이름을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("1~50자 정상 입력이면, 브랜드 이름이 생성된다.")
        @Test
        void createsBrandName_whenValueIsValid() {
            // arrange & act
            BrandName brandName = new BrandName("나이키");

            // assert
            assertThat(brandName.value()).isEqualTo("나이키");
        }

        @DisplayName("null 또는 공백 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsNullOrBlank() {
            // act & assert
            CoreException nullResult = assertThrows(CoreException.class, () ->
                new BrandName(null));
            assertThat(nullResult.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

            CoreException blankResult = assertThrows(CoreException.class, () ->
                new BrandName("   "));
            assertThat(blankResult.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("51자 이상이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenValueExceeds50Characters() {
            // arrange
            String longName = "a".repeat(51);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandName(longName));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
