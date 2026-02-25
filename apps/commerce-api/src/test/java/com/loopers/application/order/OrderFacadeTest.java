package com.loopers.application.order;

import com.loopers.application.cart.CartAppService;
import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    private OrderFacade orderFacade;
    private OrderAppService orderAppService;
    private ProductAppService productAppService;
    private CartAppService cartAppService;

    @BeforeEach
    void setUp() {
        orderAppService = mock(OrderAppService.class);
        productAppService = mock(ProductAppService.class);
        cartAppService = mock(CartAppService.class);
        orderFacade = new OrderFacade(orderAppService, productAppService, cartAppService);
    }

    private Option createOption(Long optionId, Long productId, int stock) {
        return Option.of(optionId, productId, "기본 옵션", Money.of(1000L), stock, false);
    }

    private Product createProduct(Long productId, Long brandId) {
        return Product.of(productId, brandId, "테스트 상품", Money.of(10000L), false);
    }

    @Nested
    @DisplayName("직접 주문 생성")
    class CreateOrderTest {

        @Test
        @DisplayName("직접 주문을 생성하면 재고를 차감하고 Order를 생성한다")
        void createOrder_success() {
            // given
            Long userId = 1L;
            Long optionId = 100L;
            Long productId = 10L;
            int quantity = 2;

            Option option = createOption(optionId, productId, 98);
            Product product = createProduct(productId, 1L);
            Order savedOrder = Order.of(1L, userId,
                    List.of(OrderItem.of(optionId, "테스트 상품", "기본 옵션", Money.of(11000L), quantity)),
                    OrderStatus.PENDING);

            OrderCreateCommand command = new OrderCreateCommand(userId,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, quantity)));

            given(productAppService.decreaseStock(optionId, quantity)).willReturn(option);
            given(productAppService.getById(productId)).willReturn(product);
            given(orderAppService.create(eq(userId), any())).willReturn(savedOrder);

            // when
            Order result = orderFacade.createOrder(command);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(productAppService).decreaseStock(optionId, quantity);
            verify(orderAppService).create(eq(userId), any());
        }
    }

    @Nested
    @DisplayName("장바구니 주문 생성")
    class CreateOrderFromCartTest {

        @Test
        @DisplayName("장바구니 항목으로 주문을 생성하면 재고 차감 후 장바구니를 삭제한다")
        void createOrderFromCart_success() {
            // given
            Long userId = 1L;
            Long cartItemId = 1L;
            Long optionId = 100L;
            Long productId = 10L;
            int quantity = 3;

            CartItem cartItem = CartItem.of(cartItemId, userId, optionId, quantity);
            Option option = createOption(optionId, productId, 97);
            Product product = createProduct(productId, 1L);
            Order savedOrder = Order.of(1L, userId,
                    List.of(OrderItem.of(optionId, "테스트 상품", "기본 옵션", Money.of(11000L), quantity)),
                    OrderStatus.PENDING);

            given(cartAppService.getByIds(List.of(cartItemId))).willReturn(List.of(cartItem));
            given(productAppService.decreaseStock(optionId, quantity)).willReturn(option);
            given(productAppService.getById(productId)).willReturn(product);
            given(orderAppService.create(eq(userId), any())).willReturn(savedOrder);

            // when
            Order result = orderFacade.createOrderFromCart(userId, List.of(cartItemId));

            // then
            assertThat(result.getId()).isEqualTo(1L);
            verify(productAppService).decreaseStock(optionId, quantity);
            verify(cartAppService).deleteByIds(List.of(cartItemId));
        }

        @Test
        @DisplayName("타인의 장바구니 항목으로 주문하면 예외가 발생한다")
        void createOrderFromCart_notOwner() {
            // given
            Long userId = 1L;
            Long otherUserId = 999L;
            CartItem otherUserItem = CartItem.of(1L, otherUserId, 100L, 2);

            given(cartAppService.getByIds(List.of(1L))).willReturn(List.of(otherUserItem));

            // when & then
            assertThatThrownBy(() -> orderFacade.createOrderFromCart(userId, List.of(1L)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 장바구니 항목만 주문할 수 있습니다.");

            verify(productAppService, never()).decreaseStock(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrderTest {

        @Test
        @DisplayName("주문을 취소하면 재고를 복원한다")
        void cancelOrder_success() {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            Long optionId = 100L;
            int quantity = 5;

            Order order = Order.of(orderId, userId,
                    List.of(OrderItem.of(optionId, "테스트 상품", "기본 옵션", Money.of(11000L), quantity)),
                    OrderStatus.PENDING);
            Order canceledOrder = Order.of(orderId, userId,
                    List.of(OrderItem.of(optionId, "테스트 상품", "기본 옵션", Money.of(11000L), quantity)),
                    OrderStatus.CANCELED);

            given(orderAppService.getById(orderId)).willReturn(order);
            given(orderAppService.cancel(orderId)).willReturn(canceledOrder);

            // when
            Order result = orderFacade.cancelOrder(userId, orderId);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
            verify(productAppService).increaseStock(optionId, quantity);
            verify(orderAppService).cancel(orderId);
        }

        @Test
        @DisplayName("타인의 주문을 취소하면 예외가 발생한다")
        void cancelOrder_notOwner() {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            Order order = Order.of(orderId, 999L,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.PENDING);

            given(orderAppService.getById(orderId)).willReturn(order);

            // when & then
            assertThatThrownBy(() -> orderFacade.cancelOrder(userId, orderId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 주문만 조회/취소할 수 있습니다.");

            verify(productAppService, never()).increaseStock(anyLong(), anyInt());
            verify(orderAppService, never()).cancel(anyLong());
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrderTest {

        @Test
        @DisplayName("본인의 주문을 조회할 수 있다")
        void getOrder_success() {
            // given
            Long userId = 1L;
            Long orderId = 1L;
            Order order = Order.of(orderId, userId,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.PENDING);

            given(orderAppService.getById(orderId)).willReturn(order);

            // when
            Order result = orderFacade.getOrder(userId, orderId);

            // then
            assertThat(result.getId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("타인의 주문을 조회하면 예외가 발생한다")
        void getOrder_notOwner() {
            // given
            Order order = Order.of(1L, 999L,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.PENDING);
            given(orderAppService.getById(1L)).willReturn(order);

            // when & then
            assertThatThrownBy(() -> orderFacade.getOrder(1L, 1L))
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
            // given
            Order paidOrder = Order.of(1L, 1L,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.PAID);
            given(orderAppService.pay(1L)).willReturn(paidOrder);

            // when
            Order result = orderFacade.payOrder(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderAppService).pay(1L);
        }

        @Test
        @DisplayName("주문 준비 처리를 위임한다")
        void prepareOrder() {
            // given
            Order preparingOrder = Order.of(1L, 1L,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.PREPARING);
            given(orderAppService.prepare(1L)).willReturn(preparingOrder);

            // when
            Order result = orderFacade.prepareOrder(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("주문 배송 처리를 위임한다")
        void shipOrder() {
            // given
            Order shippedOrder = Order.of(1L, 1L,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.SHIPPED);
            given(orderAppService.ship(1L)).willReturn(shippedOrder);

            // when
            Order result = orderFacade.shipOrder(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("주문 배송 완료 처리를 위임한다")
        void deliverOrder() {
            // given
            Order deliveredOrder = Order.of(1L, 1L,
                    List.of(OrderItem.of(100L, "상품", "옵션", Money.of(10000L), 1)),
                    OrderStatus.DELIVERED);
            given(orderAppService.deliver(1L)).willReturn(deliveredOrder);

            // when
            Order result = orderFacade.deliverOrder(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }
    }
}
