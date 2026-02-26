package com.loopers.domain.model.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTest {

    @Test
    @DisplayName("유효한 가격 생성 성공")
    void create_success() {
        Price price = Price.of(10000);
        assertThat(price.getValue()).isEqualTo(10000);
    }

    @Test
    @DisplayName("가격 0원 생성 성공")
    void create_success_zero() {
        Price price = Price.of(0);
        assertThat(price.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("음수 가격 생성 시 예외")
    void create_fail_negative() {
        assertThatThrownBy(() -> Price.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }
}
