package com.loopers.domain.model.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandNameTest {

    @Test
    @DisplayName("유효한 브랜드 이름 생성 성공")
    void create_success() {
        BrandName name = BrandName.of("Nike");
        assertThat(name.getValue()).isEqualTo("Nike");
    }

    @Test
    @DisplayName("브랜드 이름 null이면 예외")
    void create_fail_null() {
        assertThatThrownBy(() -> BrandName.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드 이름은 필수 입력값입니다.");
    }

    @Test
    @DisplayName("브랜드 이름 공백이면 예외")
    void create_fail_blank() {
        assertThatThrownBy(() -> BrandName.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드 이름은 필수 입력값입니다.");
    }

    @Test
    @DisplayName("브랜드 이름 50자 초과면 예외")
    void create_fail_too_long() {
        String longName = "a".repeat(51);
        assertThatThrownBy(() -> BrandName.of(longName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~50자");
    }

    @Test
    @DisplayName("브랜드 이름 공백 trim 처리")
    void create_success_with_trim() {
        BrandName name = BrandName.of("  Nike  ");
        assertThat(name.getValue()).isEqualTo("Nike");
    }

    @Test
    @DisplayName("동일한 이름은 equals 동등")
    void equals_consistency() {
        BrandName name1 = BrandName.of("Nike");
        BrandName name2 = BrandName.of("Nike");
        assertThat(name1).isEqualTo(name2);
    }
}
