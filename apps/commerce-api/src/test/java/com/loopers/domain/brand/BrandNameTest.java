package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandNameTest {

    @DisplayName("BrandName: 유효한 이름이면 생성된다")
    @Test
    void create_success() {
        BrandName brandName = new BrandName("LOOPERS");

        assertThat(brandName.value()).isEqualTo("LOOPERS");
    }

    @DisplayName("BrandName: 이름이 null/empty/blank이면 예외가 발생한다")
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void create_fail_blank(String invalidName) {
        assertThatThrownBy(() -> new BrandName(invalidName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("브랜드명은 필수입니다");
    }

    @DisplayName("BrandName: 이름이 100자를 초과하면 예외가 발생한다")
    @Test
    void create_fail_tooLong() {
        String tooLongName = "a".repeat(101);

        assertThatThrownBy(() -> new BrandName(tooLongName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("브랜드명은 100자 이하여야 합니다");
    }

    @DisplayName("BrandName: 정확히 100자이면 생성된다")
    @Test
    void create_success_exactly100Chars() {
        String maxLengthName = "a".repeat(100);

        BrandName brandName = new BrandName(maxLengthName);

        assertThat(brandName.value()).hasSize(100);
    }

    @DisplayName("BrandName: 앞뒤 공백은 trim되어 저장된다")
    @Test
    void create_success_trimsWhitespace() {
        BrandName brandName = new BrandName("  LOOPERS  ");

        assertThat(brandName.value()).isEqualTo("LOOPERS");
    }
}
