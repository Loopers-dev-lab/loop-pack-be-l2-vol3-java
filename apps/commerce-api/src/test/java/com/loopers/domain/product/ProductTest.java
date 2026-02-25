package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_상품이_생성된다() {
            Product product = Product.create(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("운동화");
            assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(product.getStockQuantity()).isEqualTo(100);
            assertThat(product.getDescription()).isEqualTo("편한 운동화");
        }

        @Test
        void likeCount가_0으로_초기화된다() {
            Product product = Product.create(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @Test
        void 설명이_null이면_null로_생성된다() {
            Product product = Product.create(1L, "운동화", new BigDecimal("50000"), 100, null);

            assertThat(product.getDescription()).isNull();
        }

        @Test
        void 브랜드ID가_null이면_예외() {
            assertThatThrownBy(() -> Product.create(null, "운동화", new BigDecimal("50000"), 100, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("브랜드 ID는 필수입니다");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 상품명이_null_또는_빈값이면_예외(String name) {
            assertThatThrownBy(() -> Product.create(1L, name, new BigDecimal("50000"), 100, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("상품명은 필수입니다");
        }

        @Test
        void 상품명이_200자를_초과하면_예외() {
            String longName = "a".repeat(201);

            assertThatThrownBy(() -> Product.create(1L, longName, new BigDecimal("50000"), 100, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("상품명은 200자 이하여야 합니다");
        }

        @Test
        void 상품명이_200자이면_성공() {
            String maxName = "a".repeat(200);

            assertThatCode(() -> Product.create(1L, maxName, new BigDecimal("50000"), 100, "설명"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 가격이_null이면_예외() {
            assertThatThrownBy(() -> Product.create(1L, "운동화", null, 100, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("가격은 필수입니다");
        }

        @Test
        void 가격이_음수면_예외() {
            assertThatThrownBy(() -> Product.create(1L, "운동화", new BigDecimal("-1"), 100, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("가격은 0 이상이어야 합니다");
        }

        @Test
        void 가격이_0이면_성공() {
            assertThatCode(() -> Product.create(1L, "운동화", BigDecimal.ZERO, 100, "설명"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 가격이_최대값을_초과하면_예외() {
            BigDecimal overMax = new BigDecimal("1000000000");

            assertThatThrownBy(() -> Product.create(1L, "운동화", overMax, 100, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("가격은 999,999,999 이하여야 합니다");
        }

        @Test
        void 가격이_최대값이면_성공() {
            BigDecimal maxPrice = new BigDecimal("999999999");

            assertThatCode(() -> Product.create(1L, "운동화", maxPrice, 100, "설명"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 재고수량이_null이면_예외() {
            assertThatThrownBy(() -> Product.create(1L, "운동화", new BigDecimal("50000"), null, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("재고 수량은 필수입니다");
        }

        @Test
        void 재고수량이_음수면_예외() {
            assertThatThrownBy(() -> Product.create(1L, "운동화", new BigDecimal("50000"), -1, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("재고 수량은 0 이상이어야 합니다");
        }

        @Test
        void 재고수량이_0이면_성공() {
            assertThatCode(() -> Product.create(1L, "운동화", new BigDecimal("50000"), 0, "설명"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 재고수량이_최대값을_초과하면_예외() {
            assertThatThrownBy(() -> Product.create(1L, "운동화", new BigDecimal("50000"), 10_000_000, "설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("재고 수량은 9,999,999 이하여야 합니다");
        }

        @Test
        void 재고수량이_최대값이면_성공() {
            assertThatCode(() -> Product.create(1L, "운동화", new BigDecimal("50000"), 9_999_999, "설명"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 설명이_1000자를_초과하면_예외() {
            String longDescription = "a".repeat(1001);

            assertThatThrownBy(() -> Product.create(1L, "운동화", new BigDecimal("50000"), 100, longDescription))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("상품 설명은 1,000자 이하여야 합니다");
        }

        @Test
        void 설명이_1000자이면_성공() {
            String maxDescription = "a".repeat(1000);

            assertThatCode(() -> Product.create(1L, "운동화", new BigDecimal("50000"), 100, maxDescription))
                    .doesNotThrowAnyException();
        }
    }
}
