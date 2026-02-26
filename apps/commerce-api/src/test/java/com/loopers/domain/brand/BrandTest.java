package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("브랜드명은 필수입니다");
        }

        @Test
        void 브랜드명이_100자를_초과하면_예외() {
            String longName = "a".repeat(101);

            assertThatThrownBy(() -> Brand.create(longName, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
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
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
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
    class 수정 {

        @Test
        void name만_수정하면_name만_변경된다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            brand.update("아디다스", null);

            assertThat(brand.getName()).isEqualTo("아디다스");
            assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @Test
        void description만_수정하면_description만_변경된다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            brand.update(null, "독일 스포츠 브랜드");

            assertThat(brand.getName()).isEqualTo("나이키");
            assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }

        @Test
        void 둘_다_수정하면_둘_다_변경된다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            brand.update("아디다스", "독일 스포츠 브랜드");

            assertThat(brand.getName()).isEqualTo("아디다스");
            assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }

        @Test
        void 둘_다_null이면_변경되지_않는다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            brand.update(null, null);

            assertThat(brand.getName()).isEqualTo("나이키");
            assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void name이_빈값이면_예외(String name) {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            assertThatThrownBy(() -> brand.update(name, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("브랜드명은 필수입니다");
        }

        @Test
        void name이_100자를_초과하면_예외() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            String longName = "a".repeat(101);

            assertThatThrownBy(() -> brand.update(longName, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("브랜드명은 100자 이하여야 합니다");
        }

        @Test
        void name이_100자이면_성공() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            String maxName = "a".repeat(100);

            brand.update(maxName, null);

            assertThat(brand.getName()).isEqualTo(maxName);
        }

        @Test
        void description이_500자를_초과하면_예외() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            String longDescription = "a".repeat(501);

            assertThatThrownBy(() -> brand.update(null, longDescription))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("브랜드 설명은 500자 이하여야 합니다");
        }

        @Test
        void description이_500자이면_성공() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            String maxDescription = "a".repeat(500);

            brand.update(null, maxDescription);

            assertThat(brand.getDescription()).isEqualTo(maxDescription);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제하면_삭제_상태이다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            brand.delete();

            assertThat(brand.isDeleted()).isTrue();
        }

        @Test
        void 이미_삭제된_브랜드를_삭제해도_삭제_상태를_유지한다() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            brand.delete();

            brand.delete();

            assertThat(brand.isDeleted()).isTrue();
        }

        @Test
        void 삭제된_브랜드를_수정하면_예외() {
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            brand.delete();

            assertThatThrownBy(() -> brand.update("아디다스", "독일 스포츠 브랜드"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
        }
    }
}
