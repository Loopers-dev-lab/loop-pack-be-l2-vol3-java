package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandInfoTest {

    @DisplayName("BrandInfo를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrandInfo_whenValidInputIsProvided() {
            // arrange
            String name = "Nike";
            String description = "Just Do It";
            String logoUrl = "https://example.com/logo.png";

            // act
            BrandInfo brandInfo = new BrandInfo(name, description, logoUrl);

            // assert
            assertAll(
                () -> assertThat(brandInfo.name()).isEqualTo(name),
                () -> assertThat(brandInfo.description()).isEqualTo(description),
                () -> assertThat(brandInfo.logoUrl()).isEqualTo(logoUrl)
            );
        }

        @DisplayName("description과 logoUrl이 null이어도, 정상적으로 생성된다.")
        @Test
        void createsBrandInfo_whenOptionalFieldsAreNull() {
            // arrange
            String name = "Nike";

            // act
            BrandInfo brandInfo = new BrandInfo(name, null, null);

            // assert
            assertAll(
                () -> assertThat(brandInfo.name()).isEqualTo(name),
                () -> assertThat(brandInfo.description()).isNull(),
                () -> assertThat(brandInfo.logoUrl()).isNull()
            );
        }

        @DisplayName("name이 null이거나 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequestException_whenNameIsNullOrEmpty(String name) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandInfo(name, "description", "https://example.com/logo.png");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 공백 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"   ", "\t", "\n"})
        void throwsBadRequestException_whenNameIsBlank(String name) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandInfo(name, "description", "https://example.com/logo.png");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 100자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameExceeds100Characters() {
            // arrange
            String name = "a".repeat(101);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandInfo(name, "description", "https://example.com/logo.png");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 정확히 100자면, 정상적으로 생성된다.")
        @Test
        void createsBrandInfo_whenNameIsExactly100Characters() {
            // arrange
            String name = "a".repeat(100);

            // act
            BrandInfo brandInfo = new BrandInfo(name, null, null);

            // assert
            assertThat(brandInfo.name()).isEqualTo(name);
        }

        @DisplayName("description이 500자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionExceeds500Characters() {
            // arrange
            String description = "a".repeat(501);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandInfo("Nike", description, "https://example.com/logo.png");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("description이 정확히 500자면, 정상적으로 생성된다.")
        @Test
        void createsBrandInfo_whenDescriptionIsExactly500Characters() {
            // arrange
            String description = "a".repeat(500);

            // act
            BrandInfo brandInfo = new BrandInfo("Nike", description, null);

            // assert
            assertThat(brandInfo.description()).isEqualTo(description);
        }

        @DisplayName("logoUrl이 URL 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"not-a-url", "ftp://example.com", "example.com/logo.png"})
        void throwsBadRequestException_whenLogoUrlIsNotValidUrl(String logoUrl) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandInfo("Nike", "description", logoUrl);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("logoUrl이 http 또는 https로 시작하면, 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"http://example.com/logo.png", "https://example.com/logo.png"})
        void createsBrandInfo_whenLogoUrlStartsWithHttpOrHttps(String logoUrl) {
            // act
            BrandInfo brandInfo = new BrandInfo("Nike", "description", logoUrl);

            // assert
            assertThat(brandInfo.logoUrl()).isEqualTo(logoUrl);
        }

        @DisplayName("logoUrl이 500자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLogoUrlExceeds500Characters() {
            // arrange
            String logoUrl = "https://example.com/" + "a".repeat(481);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandInfo("Nike", "description", logoUrl);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
