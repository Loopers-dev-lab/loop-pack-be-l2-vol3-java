package com.loopers.application.order;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderFacadeUnitTest {

    private OrderFacade orderFacade;
    private FakeProductRepository fakeProductRepository;
    private FakeUserRepository fakeUserRepository;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeUserRepository = new FakeUserRepository();
        // OrderService에는 FakeOrderRepository를 주입하지만, Facade 테스트이므로
        // OrderService 내부 저장 로직보다 Facade의 조율 로직을 검증한다.
        com.loopers.domain.order.OrderRepository fakeOrderRepository =
            new com.loopers.domain.order.FakeOrderRepository();
        OrderService orderService = new OrderService(fakeOrderRepository);
        orderFacade = new OrderFacade(orderService, fakeProductRepository, fakeUserRepository);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 주문이 생성되고 재고/포인트가 차감된다.")
        @Test
        void createsOrder_whenValidRequest() {
            // arrange
            Product product = new Product(1L, "상품A", 10000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 2)
            );

            // act
            OrderInfo result = orderFacade.createOrder(1L, items);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(1L),
                () -> assertThat(result.totalPrice()).isEqualTo(20000L),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("상품A"),
                () -> assertThat(result.items().get(0).quantity()).isEqualTo(2)
            );

            // 재고 차감 확인
            assertThat(product.getStockQuantity().value()).isEqualTo(8);
            // 포인트 차감 확인
            assertThat(user.getPoint()).isEqualTo(80000L);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenStockInsufficient() {
            // arrange
            Product product = new Product(1L, "상품A", 10000L, "설명", 1);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 5)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, items));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getMessage()).contains("재고");
        }

        @DisplayName("포인트가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenPointInsufficient() {
            // arrange
            Product product = new Product(1L, "상품A", 50000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 10000L);
            fakeUserRepository.addUserWithId(1L, user);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 2)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, items));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getMessage()).contains("포인트");
        }
    }
}
