package com.loopers.application.product;

import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderStockEventHandlerTest {

    OrderRepository orderRepository = mock(OrderRepository.class);
    ProductRepository productRepository = mock(ProductRepository.class);

    OrderStockEventHandler handler = new OrderStockEventHandler(orderRepository, productRepository);

    @DisplayName("주문 생성 이벤트 수신 시, ")
    @Nested
    class Handle {

        @DisplayName("주문 항목에 해당하는 상품의 재고가 차감된다.")
        @Test
        void decreasesStock_whenOrderCreatedEventReceived() {
            // arrange
            Long productId = 0L;
            OrderEvent.Created event = new OrderEvent.Created(1L, "20260325-ABCDEF", 150000L, null);

            OrderItem item = mock(OrderItem.class);
            when(item.productId()).thenReturn(productId);
            when(item.quantity()).thenReturn(3);

            Order order = mock(Order.class);
            when(order.items()).thenReturn(List.of(item));

            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(50000), 1L);

            when(orderRepository.findByOrderId("20260325-ABCDEF")).thenReturn(Optional.of(order));
            when(productRepository.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));

            // act
            handler.handle(event);

            // assert
            assertThat(product.stock().value()).isEqualTo(7);
        }
    }
}
