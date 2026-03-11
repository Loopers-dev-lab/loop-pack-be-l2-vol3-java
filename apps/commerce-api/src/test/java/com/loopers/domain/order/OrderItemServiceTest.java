package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceTest {

    @InjectMocks
    private OrderItemService orderItemService;

    @Nested
    @DisplayName("주문 아이템 생성")
    class CreateOrderItems {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("상품 목록이 null이거나 비어있으면, 예외를 던진다")
        void fail_when_products_is_null_or_empty(List<Product> products) {
            Long orderId = 1L;
            Map<Long, Integer> productQuantities = Map.of(1L, 2);

            assertThatThrownBy(() -> orderItemService.createOrderItems(orderId, products, productQuantities))
                .isInstanceOf(CoreException.class)
                .hasMessage("주문할 상품이 없습니다");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("주문 수량 정보가 null이거나 비어있으면, 예외를 던진다")
        void fail_when_quantities_is_null_or_empty(Map<Long, Integer> productQuantities) {
            Long orderId = 1L;
            List<Product> products = List.of(
                Product.create(1L, "상품1", 1L, 1000, 10, 0)
            );

            assertThatThrownBy(() -> orderItemService.createOrderItems(orderId, products, productQuantities))
                .isInstanceOf(CoreException.class)
                .hasMessage("주문 수량 정보가 없습니다");
        }
    }

    @Nested
    @DisplayName("총 가격 계산")
    class CalculateTotalPrice {

        @Test
        @DisplayName("상품 목록과 수량으로 총 가격을 계산한다")
        void success_calculate_total_price() {
            List<Product> products = List.of(
                Product.create(1L, "상품1", 1L, 1000, 10, 0),
                Product.create(2L, "상품2", 1L, 500, 10, 0)
            );
            Map<Long, Integer> productQuantities = Map.of(
                1L, 2,
                2L, 3
            );

            Money totalPrice = orderItemService.calculateTotalPrice(products, productQuantities);

            assertThat(totalPrice.value()).isEqualTo(3500);
        }
    }
}
