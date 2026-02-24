package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_브랜드가_생성된다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            assertThat(brand.getName()).isEqualTo("나이키");
            assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @Test
        void 설명이_null이면_null로_생성된다() {
            Brand brand = Brand.create("나이키", null);

            assertThat(brand.getName()).isEqualTo("나이키");
            assertThat(brand.getDescription()).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 브랜드명이_null_또는_빈값이면_예외(String name) {
            assertThatThrownBy(() -> Brand.create(name, "설명"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드명은 필수입니다");
        }

        @Test
        void 브랜드명이_100자를_초과하면_예외() {
            String longName = "a".repeat(101);

            assertThatThrownBy(() -> Brand.create(longName, "설명"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드명은 100자 이하여야 합니다");
        }

        @Test
        void 브랜드명이_100자이면_성공() {
            String maxName = "a".repeat(100);

            assertThatCode(() -> Brand.create(maxName, "설명"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 설명이_500자를_초과하면_예외() {
            String longDescription = "a".repeat(501);

            assertThatThrownBy(() -> Brand.create("나이키", longDescription))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드 설명은 500자 이하여야 합니다");
        }

        @Test
        void 설명이_500자이면_성공() {
            String maxDescription = "a".repeat(500);

            assertThatCode(() -> Brand.create("나이키", maxDescription))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class 삭제_상태_확인 {

        @Test
        void 삭제하면_삭제_상태이다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            brand.delete();

            assertThat(brand.isDeleted()).isTrue();
        }
    }
}
