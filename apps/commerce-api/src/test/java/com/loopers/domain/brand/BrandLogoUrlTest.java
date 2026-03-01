package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BrandLogoUrlTest {

    @DisplayName("브랜드 로고 URL을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 URL이면, 정상적으로 생성된다.")
        @Test
        void createsBrandLogoUrl_whenUrlIsValid() {
            // arrange
            var url = "https://example.com/logo.png";

            // act
            var brandLogoUrl = new BrandLogoUrl(url);

            // assert
            assertThat(brandLogoUrl.getValue()).isEqualTo(url);
        }

        @DisplayName("URL이 null이면, REQUIRED_BRAND_LOGO_URL 예외가 발생한다.")
        @Test
        void throwsRequiredBrandLogoUrlException_whenUrlIsNull() {
            assertThatThrownBy(() -> new BrandLogoUrl(null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_BRAND_LOGO_URL));
        }
    }
}