package com.loopers.application.order;

import com.loopers.domain.order.InMemoryOrderItemRepository;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
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


class OrderServiceTest {

    private InMemoryOrderRepository orderRepository;
    private InMemoryOrderItemRepository orderItemRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        orderService = new OrderService(orderRepository, orderItemRepository);
    }

    @DisplayName("주문 항목 검증 시, ")
    @Nested
    class ValidateItems {

        @DisplayName("항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.validateItems(List.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("중복 상품이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProducts() {
            // arrange
            List<OrderItemCommand> items = List.of(
                    new OrderItemCommand(1L, 1),
                    new OrderItemCommand(1L, 2)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.validateItems(items);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상적으로 OrderInfo를 반환한다.")
        @Test
        void createsOrderInfo_whenValid() {
            // arrange
            long userId = 1L;
            List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 2));
            long expectedTotal = 150000L * 2;

            // act
            OrderInfo order = orderService.placeOrder(userId, snapshots);

            // assert
            assertAll(
                    () -> assertThat(order.userId()).isEqualTo(userId),
                    () -> assertThat(order.totalAmount()).isEqualTo(expectedTotal),
                    () -> assertThat(order.status()).isEqualTo(Order.Status.ORDERED)
            );
        }
    }

    @DisplayName("주문 단건 조회 시, ")
    @Nested
    class GetOrder {

        @DisplayName("타인의 주문을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotOwned() {
            // arrange
            long ownerId = 1L;
            List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 1));
            OrderInfo order = orderService.placeOrder(ownerId, snapshots);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.getOrder(999L, order.id());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
