package com.loopers.application.product;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Scaffold: enable after Product module implementation is added")
@DisplayName("Product Application Service Tests")
class ProductServiceTest {

    @Nested
    @DisplayName("상품 등록")
    class Register {

        @Test
        @DisplayName("상품등록_성공")
        void registerSuccess() {
            assertThat(true).isTrue();
        }

        @Test
        @DisplayName("상품등록_브랜드미존재_예외")
        void registerFailWhenBrandNotFound() {
            assertThat(true).isTrue();
        }
    }
}
