package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CartItem 도메인 테스트")
class CartItemTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 장바구니 항목을 생성할 수 있다")
        void createCartItem() {
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            assertThat(cartItem.getId()).isNull();
            assertThat(cartItem.getUserId()).isEqualTo(1L);
            assertThat(cartItem.getOptionId()).isEqualTo(100L);
            assertThat(cartItem.getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("사용자 ID가 null이면 예외가 발생한다")
        void createCartItem_nullUserId() {
            assertThatThrownBy(() -> CartItem.create(null, 100L, 3))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("사용자 ID는 필수입니다.");
        }

        @Test
        @DisplayName("옵션 ID가 null이면 예외가 발생한다")
        void createCartItem_nullOptionId() {
            assertThatThrownBy(() -> CartItem.create(1L, null, 3))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("옵션 ID는 필수입니다.");
        }

        @Test
        @DisplayName("수량이 0 이하이면 예외가 발생한다")
        void createCartItem_invalidQuantity() {
            assertThatThrownBy(() -> CartItem.create(1L, 100L, 0))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다.");
        }
    }

    @Nested
    @DisplayName("of 팩토리 메서드 테스트")
    class OfTest {

        @Test
        @DisplayName("ID를 포함한 장바구니 항목을 생성할 수 있다")
        void ofCartItem() {
            CartItem cartItem = CartItem.of(1L, 2L, 100L, 5);

            assertThat(cartItem.getId()).isEqualTo(1L);
            assertThat(cartItem.getUserId()).isEqualTo(2L);
            assertThat(cartItem.getOptionId()).isEqualTo(100L);
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("수량 증가 테스트")
    class AddQuantityTest {

        @Test
        @DisplayName("수량을 증가시킬 수 있다")
        void addQuantity() {
            CartItem cartItem = CartItem.of(1L, 2L, 100L, 3);

            cartItem.addQuantity(5);

            assertThat(cartItem.getQuantity()).isEqualTo(8);
        }

        @Test
        @DisplayName("0 이하의 수량을 추가하면 예외가 발생한다")
        void addQuantity_invalidQuantity() {
            CartItem cartItem = CartItem.of(1L, 2L, 100L, 3);

            assertThatThrownBy(() -> cartItem.addQuantity(0))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다.");
        }
    }

    @Nested
    @DisplayName("수량 변경 테스트")
    class UpdateQuantityTest {

        @Test
        @DisplayName("수량을 변경할 수 있다")
        void updateQuantity() {
            CartItem cartItem = CartItem.of(1L, 2L, 100L, 3);

            cartItem.updateQuantity(10);

            assertThat(cartItem.getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("0 이하의 수량으로 변경하면 예외가 발생한다")
        void updateQuantity_invalidQuantity() {
            CartItem cartItem = CartItem.of(1L, 2L, 100L, 3);

            assertThatThrownBy(() -> cartItem.updateQuantity(-1))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("수량은 1 이상이어야 합니다.");
        }
    }
}
