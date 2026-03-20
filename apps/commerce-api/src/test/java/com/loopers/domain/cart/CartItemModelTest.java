package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CartItemModel 도메인 모델 테스트")
class CartItemModelTest {

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 성공")
        void create_WithValidInputs_ShouldSuccess() {
            CartItemModel cartItem = CartItemModel.create(1L, 1L, 3);

            assertThat(cartItem.getUserId()).isEqualTo(1L);
            assertThat(cartItem.getProductId()).isEqualTo(1L);
            assertThat(cartItem.getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("수량이 0이면 CoreException 발생")
        void create_WithZeroQuantity_ShouldThrow() {
            assertThatThrownBy(() -> CartItemModel.create(1L, 1L, 0))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("수량이 음수이면 CoreException 발생")
        void create_WithNegativeQuantity_ShouldThrow() {
            assertThatThrownBy(() -> CartItemModel.create(1L, 1L, -1))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("수량 변경")
    class QuantityTests {

        @Test
        @DisplayName("유효한 수량으로 변경 성공")
        void changeQuantity_WithValidQty_ShouldUpdate() {
            CartItemModel cartItem = createTestCartItem();
            cartItem.changeQuantity(5);
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("0으로 수량 변경 시 CoreException 발생")
        void changeQuantity_WithZeroQty_ShouldThrow() {
            CartItemModel cartItem = createTestCartItem();
            assertThatThrownBy(() -> cartItem.changeQuantity(0))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("음수로 수량 변경 시 CoreException 발생")
        void changeQuantity_WithNegativeQty_ShouldThrow() {
            CartItemModel cartItem = createTestCartItem();
            assertThatThrownBy(() -> cartItem.changeQuantity(-1))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("mergeQuantity 호출 시 기존 수량에 추가")
        void mergeQuantity_ShouldAddToExisting() {
            CartItemModel cartItem = createTestCartItem();
            cartItem.mergeQuantity(5);
            assertThat(cartItem.getQuantity()).isEqualTo(8); // 3 + 5
        }
    }

    // === Helper ===

    private CartItemModel createTestCartItem() {
        return CartItemModel.create(1L, 1L, 3);
    }
}
