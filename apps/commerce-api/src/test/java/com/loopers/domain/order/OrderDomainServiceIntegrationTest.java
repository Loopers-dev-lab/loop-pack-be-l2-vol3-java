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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderDomainServiceIntegrationTest {

    @Autowired
    private OrderDomainService orderService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Order createTestOrder(Long userId) {
        List<OrderItemCommand> items = List.of(
            new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 2)
        );
        return orderService.createOrder(userId, items);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("올바른 정보이면, 주문과 주문 항목이 생성되고 총 금액이 계산된다.")
        @Test
        void createsOrderAndItems_whenValidInfo() {
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "에어맥스", new Money(129000), "나이키", 2),
                new OrderItemCommand(2L, "에어포스1", new Money(109000), "나이키", 1)
            );

            Order order = orderService.createOrder(1L, items);

            assertAll(
                () -> assertThat(order.getId()).isNotNull(),
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(367000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED)
            );

            List<OrderItem> orderItems = order.getItems();
            assertAll(
                () -> assertThat(orderItems).hasSize(2),
                () -> assertThat(orderItems.get(0).getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(orderItems.get(0).getBrandName()).isEqualTo("나이키"),
                () -> assertThat(orderItems.get(1).getProductName()).isEqualTo("에어포스1")
            );
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.createOrder(1L, List.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 null이면, BAD_REQUEST 예외가 발생한다.")
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
    }

    @DisplayName("주문을 ID로 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            Order created = createTestOrder(1L);

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

    @DisplayName("주문을 ID로 항목과 함께 조회할 때, ")
    @Nested
    class GetByIdWithItems {

        @DisplayName("존재하는 주문이면, 주문과 항목을 반환한다.")
        @Test
        void returnsOrderWithItems_whenOrderExists() {
            Order created = createTestOrder(1L);

            Order result = orderService.getByIdWithItems(created.getId());

            assertAll(
                () -> assertThat(result.getId()).isEqualTo(created.getId()),
                () -> assertThat(result.getItems()).hasSize(1),
                () -> assertThat(result.getItems().get(0).getProductName()).isEqualTo("에어맥스")
            );
        }
    }

    @DisplayName("유저의 주문을 조회할 때, ")
    @Nested
    class GetByIdAndUserId {

        @DisplayName("본인의 주문이면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            Order created = createTestOrder(1L);

            Order result = orderService.getByIdAndUserId(created.getId(), 1L);

            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @DisplayName("다른 유저의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            Order created = createTestOrder(1L);

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
            createTestOrder(1L);
            createTestOrder(1L);
            createTestOrder(2L);

            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end = LocalDate.now().plusDays(1);

            PageResult<Order> result = orderService.getMyOrders(1L, start, end, 0, 20);

            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("기간 내 주문이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoOrdersInRange() {
            createTestOrder(1L);

            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end = LocalDate.now().plusDays(2);

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
            createTestOrder(1L);
            createTestOrder(2L);
            createTestOrder(3L);

            PageResult<Order> result = orderService.getAllOrders(0, 2);

            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.totalElements()).isEqualTo(3),
                () -> assertThat(result.totalPages()).isEqualTo(2)
            );
        }
    }
}
