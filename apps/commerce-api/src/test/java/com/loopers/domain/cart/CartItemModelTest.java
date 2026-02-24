package com.loopers.domain.cart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartItemModelTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long OPTION_ID = 10L;
    private static final int QUANTITY = 2;

    @DisplayName("create 시")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면 생성된다.")
        @Test
        void create_withValidInputs_shouldSucceed() {
            // when
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // then
            assertThat(item.getUserId()).isEqualTo(USER_ID);
            assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getOptionId()).isEqualTo(OPTION_ID);
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
        }

        @DisplayName("optionId가 null이어도 생성된다.")
        @Test
        void create_withNullOptionId_shouldSucceed() {
            // when
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, null, QUANTITY);

            // then
            assertThat(item.getOptionId()).isNull();
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
        }

        @DisplayName("userId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullUserId_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                CartItemModel.create(null, PRODUCT_ID, OPTION_ID, QUANTITY));
        }

        @DisplayName("productId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullProductId_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                CartItemModel.create(USER_ID, null, OPTION_ID, QUANTITY));
        }

        @DisplayName("수량이 0이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withZeroQuantity_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, 0));
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegativeQuantity_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, -1));
        }
    }

    @DisplayName("isSameProduct 시")
    @Nested
    class IsSameProduct {

        @DisplayName("동일 productId와 optionId면 true를 반환한다.")
        @Test
        void isSameProduct_whenSameProductAndOption_shouldReturnTrue() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when
            boolean result = item.isSameProduct(PRODUCT_ID, OPTION_ID);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("동일 productId와 optionId가 null이면 true를 반환한다.")
        @Test
        void isSameProduct_whenSameProductAndBothOptionNull_shouldReturnTrue() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, null, QUANTITY);

            // when
            boolean result = item.isSameProduct(PRODUCT_ID, null);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("productId가 다르면 false를 반환한다.")
        @Test
        void isSameProduct_whenDifferentProductId_shouldReturnFalse() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when
            boolean result = item.isSameProduct(999L, OPTION_ID);

            // then
            assertThat(result).isFalse();
        }

        @DisplayName("optionId가 다르면 false를 반환한다.")
        @Test
        void isSameProduct_whenDifferentOptionId_shouldReturnFalse() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when
            boolean result = item.isSameProduct(PRODUCT_ID, 999L);

            // then
            assertThat(result).isFalse();
        }
    }

    @DisplayName("updateQuantity 시")
    @Nested
    class UpdateQuantity {

        @DisplayName("유효한 수량이면 갱신된다.")
        @Test
        void updateQuantity_withValidQuantity_shouldUpdate() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when
            item.updateQuantity(5);

            // then
            assertThat(item.getQuantity()).isEqualTo(5);
        }

        @DisplayName("수량 1이면 갱신된다.")
        @Test
        void updateQuantity_withOne_shouldUpdate() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when
            item.updateQuantity(1);

            // then
            assertThat(item.getQuantity()).isEqualTo(1);
        }

        @DisplayName("수량이 0이면 IllegalArgumentException이 발생한다.")
        @Test
        void updateQuantity_withZero_shouldThrow() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> item.updateQuantity(0));
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void updateQuantity_withNegative_shouldThrow() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> item.updateQuantity(-1));
        }
    }
}
