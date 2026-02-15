package com.loopers.domain.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Order createTestOrder(Long userId, int totalPrice) {
        List<OrderItemCommand> items = List.of(
            new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 2)
        );
        return orderService.createOrder(userId, new Money(totalPrice), items);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("올바른 정보이면, 주문과 주문 항목이 생성된다.")
        @Test
        void createsOrderAndItems_whenValidInfo() {
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 2),
                new OrderItemCommand(2L, "에어포스1", new Money(109000), "나이키", 1)
            );

            Order order = orderService.createOrder(1L, new Money(367000), items);

            assertAll(
                () -> assertThat(order.getId()).isNotNull(),
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(367000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED)
            );

            List<OrderItem> orderItems = orderService.getOrderItems(order.getId());
            assertAll(
                () -> assertThat(orderItems).hasSize(2),
                () -> assertThat(orderItems.get(0).getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(orderItems.get(0).getBrandName()).isEqualTo("나이키"),
                () -> assertThat(orderItems.get(1).getProductName()).isEqualTo("에어포스1")
            );
        }
    }

    @DisplayName("주문을 ID로 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            Order created = createTestOrder(1L, 258000);

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
            Order created = createTestOrder(1L, 258000);

            Order result = orderService.getByIdAndUserId(created.getId(), 1L);

            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @DisplayName("다른 유저의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            Order created = createTestOrder(1L, 258000);

            CoreException result = assertThrows(CoreException.class,
                () -> orderService.getByIdAndUserId(created.getId(), 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 주문 목록을 조회할 때, ")
    @Nested
    class GetMyOrders {

        @DisplayName("기간 내 주문이 있으면, 목록을 반환한다.")
        @Test
        void returnsOrders_whenOrdersExistInRange() {
            createTestOrder(1L, 258000);
            createTestOrder(1L, 109000);
            createTestOrder(2L, 50000);

            ZonedDateTime start = ZonedDateTime.now().minusDays(1);
            ZonedDateTime end = ZonedDateTime.now().plusDays(1);

            PageResult<Order> result = orderService.getMyOrders(1L, start, end, 0, 20);

            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("기간 내 주문이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoOrdersInRange() {
            createTestOrder(1L, 258000);

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = ZonedDateTime.now().plusDays(2);

            PageResult<Order> result = orderService.getMyOrders(1L, start, end, 0, 20);

            assertThat(result.items()).isEmpty();
        }
    }

    @DisplayName("전체 주문 목록을 조회할 때, ")
    @Nested
    class GetAllOrders {

        @DisplayName("주문이 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenOrdersExist() {
            createTestOrder(1L, 258000);
            createTestOrder(2L, 109000);
            createTestOrder(3L, 50000);

            PageResult<Order> result = orderService.getAllOrders(0, 2);

            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.totalElements()).isEqualTo(3),
                () -> assertThat(result.totalPages()).isEqualTo(2)
            );
        }
    }
}
