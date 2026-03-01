package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class ProductThumbnailUrlTest {

    @DisplayName("ProductThumbnailUrl을 생성할 때,")
    @Nested
    class Constructor {

        @DisplayName("유효한 URL이면, 정상 생성된다.")
        @Test
        void success() {
            // arrange
            var value = "http://example.com/thumbnail.jpg";

            // act
            var thumbnailUrl = new ProductThumbnailUrl(value);

            // assert
            assertThat(thumbnailUrl.getValue()).isEqualTo(value);
        }

        @DisplayName("URL이 null이면, REQUIRED_PRODUCT_THUMBNAIL_URL 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenNull(String value) {
            assertThatThrownBy(() -> new ProductThumbnailUrl(value))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_PRODUCT_THUMBNAIL_URL.getMessage());
        }
    }
}