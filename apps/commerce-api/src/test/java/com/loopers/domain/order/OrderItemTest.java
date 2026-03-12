package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("OrderItem을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenInfoIsValid() {
            // arrange
            Long productId = 100L;
            String productName = "Air Max 90";
            int quantity = 2;
            Money price = new Money(150000);

            // act
            OrderItem item = OrderItem.create(productId, productName, quantity, price);

            // assert
            assertThat(item.getProductId()).isEqualTo(productId);
            assertThat(item.getProductName()).isEqualTo(productName);
            assertThat(item.getQuantity()).isEqualTo(quantity);
            assertThat(item.getPriceSnapshot()).isEqualTo(price);
        }

        @DisplayName("ID는 null로 생성된다.")
        @Test
        void createsWithNullId() {
            // act
            OrderItem item = OrderItem.create(100L, "상품", 1, new Money(10000));

            // assert
            assertThat(item.getId()).isNull();
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsZero() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItem.create(100L, "상품", 0, new Money(10000));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItem.create(100L, "상품", -1, new Money(10000));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소계(subtotal)를 계산할 때,")
    @Nested
    class GetSubtotal {

        @DisplayName("단가 × 수량이 반환된다.")
        @Test
        void returnsMultipliedPrice() {
            // arrange
            OrderItem item = OrderItem.create(100L, "상품", 3, new Money(10000));

            // act
            Money subtotal = item.getSubtotal();

            // assert
            assertThat(subtotal.amount()).isEqualTo(30000);
        }

        @DisplayName("수량이 1이면, 단가가 반환된다.")
        @Test
        void returnsPriceWhenQuantityIsOne() {
            // arrange
            OrderItem item = OrderItem.create(100L, "상품", 1, new Money(10000));

            // act
            Money subtotal = item.getSubtotal();

            // assert
            assertThat(subtotal.amount()).isEqualTo(10000);
        }
    }

    @DisplayName("OrderItem을 복원할 때,")
    @Nested
    class Reconstitute {

        @DisplayName("모든 필드가 복원된다.")
        @Test
        void reconstitutesAllFields() {
            // arrange
            Long id = 1L;
            Long productId = 100L;
            String productName = "Air Max 90";
            int quantity = 2;
            Money priceSnapshot = new Money(150000);

            // act
            OrderItem item = OrderItem.reconstitute(id, productId, productName, quantity, priceSnapshot);

            // assert
            assertThat(item.getId()).isEqualTo(id);
            assertThat(item.getProductId()).isEqualTo(productId);
            assertThat(item.getProductName()).isEqualTo(productName);
            assertThat(item.getQuantity()).isEqualTo(quantity);
            assertThat(item.getPriceSnapshot()).isEqualTo(priceSnapshot);
        }
    }
}
