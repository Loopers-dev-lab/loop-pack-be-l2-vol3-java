package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductSortTypeTest {

    @DisplayName("ProductSortType.from()을 호출할 때, ")
    @Nested
    class From {

        @DisplayName("null이면, LATEST를 반환한다.")
        @Test
        void returnsLatest_whenNull() {
            assertThat(ProductSortType.from(null)).isEqualTo(ProductSortType.LATEST);
        }

        @DisplayName("빈 문자열이면, LATEST를 반환한다.")
        @Test
        void returnsLatest_whenBlank() {
            assertThat(ProductSortType.from("")).isEqualTo(ProductSortType.LATEST);
        }

        @DisplayName("소문자 'latest'이면, LATEST를 반환한다.")
        @Test
        void returnsLatest_whenLowercase() {
            assertThat(ProductSortType.from("latest")).isEqualTo(ProductSortType.LATEST);
        }

        @DisplayName("대문자 'PRICE_ASC'이면, PRICE_ASC를 반환한다.")
        @Test
        void returnsPriceAsc_whenUppercase() {
            assertThat(ProductSortType.from("PRICE_ASC")).isEqualTo(ProductSortType.PRICE_ASC);
        }

        @DisplayName("소문자 'likes_desc'이면, LIKES_DESC를 반환한다.")
        @Test
        void returnsLikesDesc_whenLowercase() {
            assertThat(ProductSortType.from("likes_desc")).isEqualTo(ProductSortType.LIKES_DESC);
        }

        @DisplayName("유효하지 않은 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInvalid() {
            CoreException result = assertThrows(CoreException.class,
                () -> ProductSortType.from("invalid"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
