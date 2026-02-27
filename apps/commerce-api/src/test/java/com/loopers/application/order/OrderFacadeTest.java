package com.loopers.application.order;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.order.InMemoryOrderItemRepository;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderFacadeTest {

    private InMemoryProductRepository productRepository;
    private ProductApplicationService productService;
    private InMemoryOrderRepository orderRepository;
    private InMemoryOrderItemRepository orderItemRepository;
    private OrderApplicationService orderService;
    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productService = new ProductApplicationService(productRepository);
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        orderService = new OrderApplicationService(orderRepository, orderItemRepository);
        orderFacade = new OrderFacade(orderService, productService);
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    class CreateOrder {

        @DisplayName("성공하면 재고가 차감된다.")
        @Test
        void decreasesStock_whenOrderSucceeds() {
            // arrange
            long userId = 1L;
            ProductInfo product = productService.register(new ProductCreateCommand(1L, "에어맥스", null, 150000, 5));
            OrderCreateCommand command = new OrderCreateCommand(
                    userId,
                    List.of(new OrderItemCommand(product.id(), 2))
            );

            // act
            orderFacade.createOrder(command);

            // assert
            assertThat(productService.getProduct(product.id()).stockQuantity()).isEqualTo(3);
        }

        @DisplayName("성공하면 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderSucceeds() {
            // arrange
            long userId = 1L;
            ProductInfo product = productService.register(new ProductCreateCommand(1L, "에어맥스", null, 150000, 5));
            OrderCreateCommand command = new OrderCreateCommand(
                    userId,
                    List.of(new OrderItemCommand(product.id(), 2))
            );

            // act
            OrderInfo order = orderFacade.createOrder(command);

            // assert
            assertAll(
                    () -> assertThat(order.userId()).isEqualTo(userId),
                    () -> assertThat(order.totalAmount()).isEqualTo(300000L)
            );
        }
    }
}
