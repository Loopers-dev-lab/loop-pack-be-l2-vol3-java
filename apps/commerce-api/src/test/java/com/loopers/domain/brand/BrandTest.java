package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 *  브랜드 도메인 테스트
 */
class BrandTest {

    @DisplayName("브랜드 도메인 생성 성공 테스트")
    @Test
    void success_create_brand() {
        String name = "brand1";
        String description = "description1";

        Brand brand = Brand.create(null, name, description);

        Assertions.assertThat(brand.getName()).isEqualTo(name);
        Assertions.assertThat(brand.getDescription()).isEqualTo(description);
    }

    @DisplayName("브랜드 이름이 유효하지 않다면, 생성시 예외를 던진다")
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void fail_create_brand_with_invalid_name(String name) {
        String description = "description1";

        Assertions.assertThatThrownBy(() -> Brand.create(null, name, description))
            .isInstanceOf(CoreException.class)
            .hasMessage("브랜드 이름은 필수 입니다");
    }

    @DisplayName("브랜드 이름이 유효하지 않다면, 수정시 예외를 던진다")
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void fail_update_brand_with_invalid_name(String name) {
        Brand brand = Brand.create(null, "brand1", "description1");

        Assertions.assertThatThrownBy(() -> brand.update(name, "description2"))
            .isInstanceOf(CoreException.class)
            .hasMessage("브랜드 이름은 필수 입니다");
    }
}
