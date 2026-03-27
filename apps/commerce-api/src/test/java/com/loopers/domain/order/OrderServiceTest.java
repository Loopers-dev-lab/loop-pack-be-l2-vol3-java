package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.order.dto.OrderCommand;
import com.loopers.domain.order.dto.OrderInfo;
import com.loopers.support.error.CoreException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    private OrderService orderService;
    private FakeOrderRepository fakeOrderRepository;
    private FakeOrderItemRepository fakeOrderItemRepository;

    @BeforeEach
    void setUp() {
        fakeOrderRepository = new FakeOrderRepository();
        fakeOrderItemRepository = new FakeOrderItemRepository();
        orderService = new OrderService(fakeOrderRepository, fakeOrderItemRepository);
    }

    private List<OrderCommand.CreateItem> createSampleCommands() {
        return List.of(
                new OrderCommand.CreateItem(10L, 25000, 2, "상품A", "브랜드A"));
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("주문이 저장되고 totalPrice가 계산된다")
        @Test
        void createOrder_savesOrder() {
            // act
            OrderModel order = orderService.createOrder(1L, createSampleCommands());

            // assert
            assertAll(
                    () -> assertThat(order.getId()).isNotEqualTo(0L),
                    () -> assertThat(order.getUserId()).isEqualTo(1L),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(50000),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT),
                    () -> assertThat(order.getItems()).hasSize(1));
        }

        @DisplayName("빈 항목이면 예외가 발생한다")
        @Test
        void createOrder_withEmptyItems_throwsException() {
            assertThatThrownBy(() -> orderService.createOrder(1L, List.of()))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("주문을 조회할 때, ")
    @Nested
    class GetOrder {

        @DisplayName("ID로 주문을 조회한다")
        @Test
        void getById_returnsOrder() {
            // arrange
            OrderModel savedOrder = orderService.createOrder(1L, createSampleCommands());

            // act
            OrderModel foundOrder = orderService.getById(savedOrder.getId());

            // assert
            assertThat(foundOrder.getId()).isEqualTo(savedOrder.getId());
        }

        @DisplayName("존재하지 않는 주문 조회 시 예외가 발생한다")
        @Test
        void getById_notFound_throwsException() {
            assertThatThrownBy(() -> orderService.getById(999L))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("ID + userId로 본인 주문을 조회한다")
        @Test
        void getByIdAndUserId_returnsOrder() {
            // arrange
            OrderModel savedOrder = orderService.createOrder(1L, createSampleCommands());

            // act
            OrderModel foundOrder = orderService.getByIdAndUserId(savedOrder.getId(), 1L);

            // assert
            assertThat(foundOrder.getId()).isEqualTo(savedOrder.getId());
        }

        @DisplayName("본인 주문이 아니면 예외가 발생한다")
        @Test
        void getByIdAndUserId_notOwner_throwsException() {
            // arrange
            OrderModel savedOrder = orderService.createOrder(1L, createSampleCommands());

            // act & assert
            assertThatThrownBy(() -> orderService.getByIdAndUserId(savedOrder.getId(), 999L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("유저ID + 기간으로 주문 목록을 조회할 때, ")
    @Nested
    class GetOrdersByUserIdAndPeriod {

        @DisplayName("해당 유저의 주문 목록을 반환한다")
        @Test
        void getOrdersByUserIdAndPeriod_returnsOrders() {
            // arrange
            orderService.createOrder(1L, createSampleCommands());

            // act
            List<OrderModel> orders = orderService.getOrdersByUserIdAndPeriod(
                    1L,
                    java.time.ZonedDateTime.now().minusDays(1),
                    java.time.ZonedDateTime.now().plusDays(1));

            // assert
            assertThat(orders).hasSize(1);
        }
    }

    @DisplayName("전체 주문을 페이지네이션으로 조회할 때, ")
    @Nested
    class GetAllOrders {

        @DisplayName("전체 주문을 페이지네이션으로 반환한다")
        @Test
        void getAllOrders_returnsPage() {
            // arrange
            orderService.createOrder(1L, createSampleCommands());
            orderService.createOrder(2L, List.of(
                    new OrderCommand.CreateItem(20L, 30000, 1, "상품B", "브랜드B")));

            // act
            Page<OrderModel> page = orderService.getAllOrders(PageRequest.of(0, 10));

            // assert
            assertAll(
                    () -> assertThat(page.getTotalElements()).isEqualTo(2),
                    () -> assertThat(page.getContent()).hasSize(2));
        }
    }

    @DisplayName("아이템을 취소할 때, ")
    @Nested
    class CancelItem {

        @DisplayName("주문을 조회하고 아이템을 취소한다")
        @Test
        void cancelItem_success() {
            // arrange
            OrderModel order = orderService.createOrder(1L, createSampleCommands());
            Long orderItemId = order.getItems().get(0).getId();

            // act
            OrderInfo.CancelledItem result = orderService.cancelItem(order, orderItemId);

            // assert
            assertAll(
                    () -> assertThat(result.productId()).isEqualTo(10L),
                    () -> assertThat(result.quantity()).isEqualTo(2),
                    () -> assertThat(result.orderFullyCancelled()).isTrue(),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(0),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED));
        }

        @DisplayName("여러 아이템 중 하나만 취소하면 나머지 가격이 유지된다")
        @Test
        void cancelItem_partialCancel_recalculatesTotalPrice() {
            // arrange
            List<OrderCommand.CreateItem> commands = List.of(
                    new OrderCommand.CreateItem(10L, 25000, 2, "상품A", "브랜드A"),
                    new OrderCommand.CreateItem(20L, 30000, 1, "상품B", "브랜드B"));
            OrderModel order = orderService.createOrder(1L, commands);
            Long firstItemId = order.getItems().get(0).getId();

            // act
            OrderInfo.CancelledItem result = orderService.cancelItem(order, firstItemId);

            // assert
            assertAll(
                    () -> assertThat(result.orderFullyCancelled()).isFalse(),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(30000),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT));
        }
    }

    @DisplayName("미결제 주문 검증할 때, ")
    @Nested
    class ValidateNoPendingPayment {

        @DisplayName("PENDING_PAYMENT 주문이 있으면 예외가 발생한다")
        @Test
        void validateNoPendingPayment_whenExists_throwsException() {
            // arrange
            orderService.createOrder(1L, createSampleCommands());

            // act & assert
            assertThatThrownBy(() -> orderService.validateNoPendingPayment(1L))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("PENDING_PAYMENT 주문이 없으면 통과한다")
        @Test
        void validateNoPendingPayment_whenNotExists_passes() {
            // act & assert — 예외 없이 통과
            orderService.validateNoPendingPayment(1L);
        }
    }

    @DisplayName("결제 실패로 주문을 취소할 때, ")
    @Nested
    class CancelByPaymentFailure {

        @DisplayName("주문 상태가 CANCELLED로 변경되고 아이템 정보가 반환된다")
        @Test
        void cancelByPaymentFailure_cancelsAndReturnsInfo() {
            // arrange
            OrderModel order = orderService.createOrder(1L, List.of(
                    new OrderCommand.CreateItem(10L, 25000, 2, "상품A", "브랜드A"),
                    new OrderCommand.CreateItem(20L, 30000, 1, "상품B", "브랜드B")));

            // act
            OrderInfo.PaymentFailureCancellation result =
                    orderService.cancelByPaymentFailure(order.getId());

            // assert
            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED),
                    () -> assertThat(result.userId()).isEqualTo(1L),
                    () -> assertThat(result.totalPrice()).isEqualTo(80000),
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).productId()).isEqualTo(10L),
                    () -> assertThat(result.items().get(0).quantity()).isEqualTo(2),
                    () -> assertThat(result.items().get(1).productId()).isEqualTo(20L),
                    () -> assertThat(result.items().get(1).quantity()).isEqualTo(1));
        }

        @DisplayName("PENDING_PAYMENT이 아닌 주문이면 예외가 발생한다")
        @Test
        void cancelByPaymentFailure_whenNotPendingPayment_throwsException() {
            // arrange
            OrderModel order = orderService.createOrder(1L, createSampleCommands());
            orderService.completeOrder(order.getId());

            // act & assert
            assertThatThrownBy(() -> orderService.cancelByPaymentFailure(order.getId()))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("할인을 적용할 때, ")
    @Nested
    class ApplyDiscount {

        @DisplayName("할인 금액이 totalPrice에 반영된다")
        @Test
        void applyDiscount_success() {
            // arrange
            OrderModel order = orderService.createOrder(1L, createSampleCommands());

            // act
            orderService.applyDiscount(order, 5000);

            // assert
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isEqualTo(45000),
                    () -> assertThat(order.getDiscountAmount()).isEqualTo(5000));
        }

        @DisplayName("할인 적용 후 부분 취소 시 할인이 유지된다")
        @Test
        void applyDiscount_thenPartialCancel_maintainsDiscount() {
            // arrange
            List<OrderCommand.CreateItem> commands = List.of(
                    new OrderCommand.CreateItem(10L, 30000, 1, "상품A", "브랜드A"),
                    new OrderCommand.CreateItem(20L, 20000, 1, "상품B", "브랜드B"));
            OrderModel order = orderService.createOrder(1L, commands);
            orderService.applyDiscount(order, 5000);
            Long firstItemId = order.getItems().get(0).getId();

            // act
            orderService.cancelItem(order, firstItemId);

            // assert — 남은 20000 - 할인 5000 = 15000
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isEqualTo(15000),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT));
        }
    }
}
