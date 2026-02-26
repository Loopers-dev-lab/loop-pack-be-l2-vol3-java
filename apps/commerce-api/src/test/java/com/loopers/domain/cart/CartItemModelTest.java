package com.loopers.domain.cart;

import com.loopers.domain.product.Quantity;
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
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));
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
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, null, Quantity.of(QUANTITY));
            // then
            assertThat(item.getOptionId()).isNull();
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
        }

        @DisplayName("userId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullUserId_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> CartItemModel.create(null, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY)));
        }

        @DisplayName("productId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullProductId_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> CartItemModel.create(USER_ID, null, OPTION_ID, Quantity.of(QUANTITY)));
        }

        @DisplayName("수량이 0이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withZeroQuantity_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(0)));
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegativeQuantity_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(-1)));
        }
    }

    @DisplayName("isSameProduct 시")
    @Nested
    class IsSameProduct {

        @DisplayName("동일 productId와 optionId면 true를 반환한다.")
        @Test
        void isSameProduct_whenSameProductAndOption_shouldReturnTrue() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when
            boolean result = item.isSameProduct(PRODUCT_ID, OPTION_ID);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("동일 productId와 optionId가 null이면 true를 반환한다.")
        @Test
        void isSameProduct_whenSameProductAndBothOptionNull_shouldReturnTrue() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, null, Quantity.of(QUANTITY));

            // when
            boolean result = item.isSameProduct(PRODUCT_ID, null);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("productId가 다르면 false를 반환한다.")
        @Test
        void isSameProduct_whenDifferentProductId_shouldReturnFalse() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when
            boolean result = item.isSameProduct(999L, OPTION_ID);

            // then
            assertThat(result).isFalse();
        }

        @DisplayName("optionId가 다르면 false를 반환한다.")
        @Test
        void isSameProduct_whenDifferentOptionId_shouldReturnFalse() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

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
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when
            item.updateQuantity(Quantity.of(5));

            // then
            assertThat(item.getQuantity()).isEqualTo(5);
        }

        @DisplayName("수량 1이면 갱신된다.")
        @Test
        void updateQuantity_withOne_shouldUpdate() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when
            item.updateQuantity(Quantity.of(1));

            // then
            assertThat(item.getQuantity()).isEqualTo(1);
        }

        @DisplayName("수량이 0이면 IllegalArgumentException이 발생한다.")
        @Test
        void updateQuantity_withZero_shouldThrow() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when & then
            assertThrows(IllegalArgumentException.class, () -> item.updateQuantity(Quantity.of(0)));
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void updateQuantity_withNegative_shouldThrow() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when & then
            assertThrows(IllegalArgumentException.class, () -> item.updateQuantity(Quantity.of(-1)));
        }
    }

    @DisplayName("updateQuantityAndOption 시")
    @Nested
    class UpdateQuantityAndOption {

        @DisplayName("유효한 수량과 옵션이면 갱신된다.")
        @Test
        void updateQuantityAndOption_withValid_shouldUpdate() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when
            item.updateQuantityAndOption(Quantity.of(5), 20L);

            // then
            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(item.getOptionId()).isEqualTo(20L);
        }

        @DisplayName("optionId를 null로 갱신할 수 있다.")
        @Test
        void updateQuantityAndOption_withNullOption_shouldUpdate() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when
            item.updateQuantityAndOption(Quantity.of(1), null);

            // then
            assertThat(item.getQuantity()).isEqualTo(1);
            assertThat(item.getOptionId()).isNull();
        }

        @DisplayName("수량이 0이면 IllegalArgumentException이 발생한다.")
        @Test
        void updateQuantityAndOption_withZero_shouldThrow() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when & then
            assertThrows(IllegalArgumentException.class, () -> item.updateQuantityAndOption(Quantity.of(0), OPTION_ID));
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void updateQuantityAndOption_withNegative_shouldThrow() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));

            // when & then
            assertThrows(IllegalArgumentException.class, () -> item.updateQuantityAndOption(Quantity.of(-1), null));
        }
    }
}
