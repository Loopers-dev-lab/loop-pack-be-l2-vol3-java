package com.loopers.application.order;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.order.InMemoryOrderItemRepository;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFacadeTest {

    private InMemoryProductRepository productRepository;
    private ProductService productService;
    private InMemoryOrderRepository orderRepository;
    private InMemoryOrderItemRepository orderItemRepository;
    private OrderService orderService;
    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productService = new ProductService(productRepository);
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        orderService = new OrderService(orderRepository, orderItemRepository);
        orderFacade = new OrderFacade(orderService, productService);
    }

    @DisplayName("주문 생성에 성공하면 생성된 주문 정보를 반환한다.")
    @Test
    void returnsCreatedOrderInfo_whenOrderSucceeds() {
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
        assertThat(order.id()).isNotNull();
    }
}
