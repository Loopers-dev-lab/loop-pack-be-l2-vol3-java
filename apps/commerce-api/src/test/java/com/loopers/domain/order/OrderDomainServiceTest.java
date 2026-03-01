package com.loopers.domain.order;

import com.loopers.domain.product.Money;
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

class OrderDomainServiceTest {

    private OrderDomainService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderDomainService(new FakeOrderRepository());
    }

    private List<OrderItemCommand> createValidItems() {
        return List.of(
            new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 2),
            new OrderItemCommand(2L, "에어포스1", new Money(109000), "나이키", 1)
        );
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("올바른 정보이면, 주문이 생성되고 총 가격이 계산된다.")
        @Test
        void createsOrder_whenValidInfo() {
            List<OrderItemCommand> items = createValidItems();

            Order order = orderService.createOrder(1L, items);

            assertAll(
                () -> assertThat(order.getId()).isNotNull(),
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(367000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED),
                () -> assertThat(order.getItems()).hasSize(2)
            );
        }

        @DisplayName("빈 항목이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.createOrder(1L, List.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null 항목이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.createOrder(1L, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("중복된 상품이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProducts() {
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 2),
                new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 3)
            );

            CoreException result = assertThrows(CoreException.class,
                () -> orderService.createOrder(1L, items));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("총 가격이 정확히 계산된다.")
        @Test
        void calculatesTotalPrice_correctly() {
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "상품A", new Money(10000), "브랜드A", 3),
                new OrderItemCommand(2L, "상품B", new Money(20000), "브랜드B", 2)
            );

            Order order = orderService.createOrder(1L, items);

            assertThat(order.getTotalPrice()).isEqualTo(new Money(70000));
        }
    }

    @DisplayName("주문을 ID로 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            Order created = orderService.createOrder(1L, createValidItems());

            Order result = orderService.getById(created.getId());

            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.getById(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("유저의 주문을 조회할 때, ")
    @Nested
    class GetByIdAndUserId {

        @DisplayName("본인의 주문이면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            Order created = orderService.createOrder(1L, createValidItems());

            Order result = orderService.getByIdAndUserId(created.getId(), 1L);

            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @DisplayName("다른 유저의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            Order created = orderService.createOrder(1L, createValidItems());

            CoreException result = assertThrows(CoreException.class,
                () -> orderService.getByIdAndUserId(created.getId(), 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
