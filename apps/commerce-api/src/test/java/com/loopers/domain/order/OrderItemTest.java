package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderItem 값 객체 테스트")
class OrderItemTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 주문 항목을 생성할 수 있다")
        void createOrderItem() {
            OrderItem orderItem = OrderItem.of(1L, "테스트 상품", "기본 옵션", Money.of(10000L), 2);

            assertThat(orderItem.getOptionId()).isEqualTo(1L);
            assertThat(orderItem.getProductName()).isEqualTo("테스트 상품");
            assertThat(orderItem.getOptionName()).isEqualTo("기본 옵션");
            assertThat(orderItem.getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("옵션 ID가 없으면 예외가 발생한다")
        void createWithNullOptionIdThrowsException() {
            assertThatThrownBy(() -> OrderItem.of(null, "테스트 상품", "기본 옵션", Money.of(10000L), 2))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("옵션 ID는 필수");
        }

        @Test
        @DisplayName("상품명이 빈 값이면 예외가 발생한다")
        void createWithEmptyProductNameThrowsException() {
            assertThatThrownBy(() -> OrderItem.of(1L, "", "기본 옵션", Money.of(10000L), 2))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품명은 필수");
        }

        @Test
        @DisplayName("옵션명이 빈 값이면 예외가 발생한다")
        void createWithEmptyOptionNameThrowsException() {
            assertThatThrownBy(() -> OrderItem.of(1L, "테스트 상품", "", Money.of(10000L), 2))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("옵션명은 필수");
        }

        @Test
        @DisplayName("수량이 0 이하이면 예외가 발생한다")
        void createWithZeroQuantityThrowsException() {
            assertThatThrownBy(() -> OrderItem.of(1L, "테스트 상품", "기본 옵션", Money.of(10000L), 0))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("수량은 1 이상");
        }
    }

    @Nested
    @DisplayName("총 가격 계산 테스트")
    class TotalPriceTest {

        @Test
        @DisplayName("총 가격을 계산할 수 있다")
        void calculateTotalPrice() {
            OrderItem orderItem = OrderItem.of(1L, "테스트 상품", "기본 옵션", Money.of(10000L), 3);

            Money totalPrice = orderItem.getTotalPrice();

            assertThat(totalPrice.getAmount()).isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("동등성 테스트")
    class EqualityTest {

        @Test
        @DisplayName("같은 정보를 가진 주문 항목은 동등하다")
        void sameOrderItemsAreEqual() {
            OrderItem item1 = OrderItem.of(1L, "테스트 상품", "기본 옵션", Money.of(10000L), 2);
            OrderItem item2 = OrderItem.of(1L, "테스트 상품", "기본 옵션", Money.of(10000L), 2);

            assertThat(item1).isEqualTo(item2);
        }
    }
}
