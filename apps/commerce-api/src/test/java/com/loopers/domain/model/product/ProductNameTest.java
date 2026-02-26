package com.loopers.domain.model.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductNameTest {

    @Test
    @DisplayName("유효한 상품 이름 생성 성공")
    void create_success() {
        ProductName name = ProductName.of("에어맥스 90");
        assertThat(name.getValue()).isEqualTo("에어맥스 90");
    }

    @Test
    @DisplayName("상품 이름 null이면 예외")
    void create_fail_null() {
        assertThatThrownBy(() -> ProductName.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 이름은 필수 입력값입니다.");
    }

    @Test
    @DisplayName("상품 이름 100자 초과면 예외")
    void create_fail_too_long() {
        String longName = "a".repeat(101);
        assertThatThrownBy(() -> ProductName.of(longName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100자");
    }

    @Test
    @DisplayName("상품 이름 공백 trim 처리")
    void create_success_with_trim() {
        ProductName name = ProductName.of("  에어맥스 90  ");
        assertThat(name.getValue()).isEqualTo("에어맥스 90");
    }
}
