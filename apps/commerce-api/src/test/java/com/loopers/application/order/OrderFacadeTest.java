package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    private OrderFacade orderFacade;
    private OrderAppService orderAppService;

    @BeforeEach
    void setUp() {
        orderAppService = mock(OrderAppService.class);
        orderFacade = new OrderFacade(orderAppService);
    }

    @Nested
    @DisplayName("직접 주문 생성")
    class CreateOrderTest {

        @Test
        @DisplayName("직접 주문을 생성하면 OrderAppService에 위임한다")
        void createOrder_success() {
            Long userId = 1L;
            Long optionId = 100L;
            int quantity = 2;

            Order savedOrder = mock(Order.class);
            given(savedOrder.getId()).willReturn(1L);
            given(savedOrder.getStatus()).willReturn(OrderStatus.PENDING);

            OrderCreateCommand command = new OrderCreateCommand(userId,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, quantity)));

            given(orderAppService.createOrder(any(OrderCreateCommand.class))).willReturn(savedOrder);

            Order result = orderFacade.createOrder(command);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderAppService).createOrder(any(OrderCreateCommand.class));
        }
    }

    @Nested
    @DisplayName("장바구니 주문 생성")
    class CreateOrderFromCartTest {

        @Test
        @DisplayName("장바구니 주문을 생성하면 OrderAppService에 위임한다")
        void createOrderFromCart_success() {
            Long userId = 1L;
            List<Long> cartItemIds = List.of(1L, 2L);

            Order savedOrder = mock(Order.class);
            given(savedOrder.getId()).willReturn(1L);
            given(orderAppService.createOrderFromCart(eq(userId), eq(cartItemIds), eq(null))).willReturn(savedOrder);

            Order result = orderFacade.createOrderFromCart(userId, cartItemIds, null);

            assertThat(result.getId()).isEqualTo(1L);
            verify(orderAppService).createOrderFromCart(userId, cartItemIds, null);
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrderTest {

        @Test
        @DisplayName("주문을 취소하면 OrderAppService에 위임한다")
        void cancelOrder_success() {
            Long userId = 1L;
            Long orderId = 1L;

            Order canceledOrder = mock(Order.class);
            given(canceledOrder.getStatus()).willReturn(OrderStatus.CANCELED);
            given(orderAppService.cancelOrder(userId, orderId)).willReturn(canceledOrder);

            Order result = orderFacade.cancelOrder(userId, orderId);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
            verify(orderAppService).cancelOrder(userId, orderId);
        }

        @Test
        @DisplayName("타인의 주문을 취소하면 예외가 발생한다")
        void cancelOrder_notOwner() {
            Long userId = 1L;
            Long orderId = 1L;

            doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "본인의 주문만 조회/취소할 수 있습니다."))
                    .when(orderAppService).cancelOrder(userId, orderId);

            assertThatThrownBy(() -> orderFacade.cancelOrder(userId, orderId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 주문만 조회/취소할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("주문 상태 전이 (Admin)")
    class OrderStateTransitionTest {

        @Test
        @DisplayName("주문 결제 처리를 위임한다")
        void payOrder() {
            Order paidOrder = mock(Order.class);
            given(paidOrder.getStatus()).willReturn(OrderStatus.PAID);
            given(orderAppService.pay(1L)).willReturn(paidOrder);

            Order result = orderFacade.payOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderAppService).pay(1L);
        }

        @Test
        @DisplayName("주문 준비 처리를 위임한다")
        void prepareOrder() {
            Order preparingOrder = mock(Order.class);
            given(preparingOrder.getStatus()).willReturn(OrderStatus.PREPARING);
            given(orderAppService.prepare(1L)).willReturn(preparingOrder);

            Order result = orderFacade.prepareOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("주문 배송 처리를 위임한다")
        void shipOrder() {
            Order shippedOrder = mock(Order.class);
            given(shippedOrder.getStatus()).willReturn(OrderStatus.SHIPPED);
            given(orderAppService.ship(1L)).willReturn(shippedOrder);

            Order result = orderFacade.shipOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("주문 배송 완료 처리를 위임한다")
        void deliverOrder() {
            Order deliveredOrder = mock(Order.class);
            given(deliveredOrder.getStatus()).willReturn(OrderStatus.DELIVERED);
            given(orderAppService.deliver(1L)).willReturn(deliveredOrder);

            Order result = orderFacade.deliverOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }
    }
}
