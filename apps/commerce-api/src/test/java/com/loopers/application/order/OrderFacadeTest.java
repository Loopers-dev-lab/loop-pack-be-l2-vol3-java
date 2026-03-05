package com.loopers.application.order;

import com.loopers.application.cart.CartAppService;
import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    private OrderFacade orderFacade;
    private OrderAppService orderAppService;
    private ProductAppService productAppService;
    private CartAppService cartAppService;
    private MemberRepository memberRepository;
    private IssuedCouponRepository issuedCouponRepository;
    private OptionRepository optionRepository;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderAppService = mock(OrderAppService.class);
        productAppService = mock(ProductAppService.class);
        cartAppService = mock(CartAppService.class);
        memberRepository = mock(MemberRepository.class);
        issuedCouponRepository = mock(IssuedCouponRepository.class);
        optionRepository = mock(OptionRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderFacade = new OrderFacade(orderAppService, productAppService, cartAppService,
                memberRepository, issuedCouponRepository, optionRepository, orderRepository);
    }

    private Option createOption(Long optionId, Long productId, int stock) {
        Option option = mock(Option.class);
        given(option.getId()).willReturn(optionId);
        given(option.getProductId()).willReturn(productId);
        given(option.getName()).willReturn("기본 옵션");
        given(option.getAdditionalPrice()).willReturn(Money.of(1000L));
        given(option.getStock()).willReturn(stock);
        return option;
    }

    private Product createProduct(Long productId, Long brandId) {
        Product product = mock(Product.class);
        given(product.getId()).willReturn(productId);
        given(product.getBrandId()).willReturn(brandId);
        given(product.getName()).willReturn("테스트 상품");
        given(product.getBasePrice()).willReturn(Money.of(10000L));
        return product;
    }

    private Member createMember(Long id) {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(id);
        given(member.getPoint()).willReturn(Money.of(100000L));
        return member;
    }

    @Nested
    @DisplayName("직접 주문 생성")
    class CreateOrderTest {

        @Test
        @DisplayName("직접 주문을 생성하면 재고를 차감하고 Order를 생성한다")
        void createOrder_success() {
            Long userId = 1L;
            Long optionId = 100L;
            Long productId = 10L;
            int quantity = 2;

            Member member = createMember(userId);
            Option option = createOption(optionId, productId, 98);
            Product product = createProduct(productId, 1L);
            Order savedOrder = mock(Order.class);
            given(savedOrder.getId()).willReturn(1L);
            given(savedOrder.getStatus()).willReturn(OrderStatus.PENDING);

            OrderCreateCommand command = new OrderCreateCommand(userId,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, quantity)));

            given(memberRepository.findByIdWithLock(userId)).willReturn(Optional.of(member));
            given(memberRepository.save(any())).willReturn(member);
            given(optionRepository.findByIdWithLock(optionId)).willReturn(Optional.of(option));
            given(optionRepository.save(any())).willReturn(option);
            given(productAppService.getById(productId)).willReturn(product);
            given(orderAppService.create(any(Order.class))).willReturn(savedOrder);

            Order result = orderFacade.createOrder(command);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(optionRepository).findByIdWithLock(optionId);
            verify(orderAppService).create(any(Order.class));
        }
    }

    @Nested
    @DisplayName("장바구니 주문 생성")
    class CreateOrderFromCartTest {

        @Test
        @DisplayName("타인의 장바구니 항목으로 주문하면 예외가 발생한다")
        void createOrderFromCart_notOwner() {
            Long userId = 1L;
            Long otherUserId = 999L;
            CartItem otherUserItem = CartItem.of(1L, otherUserId, 100L, 2);

            given(cartAppService.getByIds(List.of(1L))).willReturn(List.of(otherUserItem));

            assertThatThrownBy(() -> orderFacade.createOrderFromCart(userId, List.of(1L), null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 장바구니 항목");

            verify(optionRepository, never()).findByIdWithLock(anyLong());
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrderTest {

        @Test
        @DisplayName("주문을 취소하면 재고를 복원한다")
        void cancelOrder_success() {
            Long userId = 1L;
            Long orderId = 1L;
            Long optionId = 100L;
            int quantity = 5;

            Order order = mock(Order.class);
            given(order.getId()).willReturn(orderId);
            given(order.getUserId()).willReturn(userId);
            given(order.getIssuedCouponId()).willReturn(null);
            given(order.getPaymentAmount()).willReturn(Money.of(55000L));
            given(order.getOrderItems()).willReturn(
                    List.of(OrderItem.of(optionId, "테스트 상품", "기본 옵션", Money.of(11000L), quantity))
            );

            Member member = createMember(userId);

            Option option = mock(Option.class);
            given(optionRepository.findByIdWithLock(optionId)).willReturn(Optional.of(option));
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
            given(memberRepository.findByIdWithLock(userId)).willReturn(Optional.of(member));

            Order result = orderFacade.cancelOrder(userId, orderId);

            verify(order).cancel();
            verify(option).increaseStock(quantity);
        }

        @Test
        @DisplayName("타인의 주문을 취소하면 예외가 발생한다")
        void cancelOrder_notOwner() {
            Long userId = 1L;
            Long orderId = 1L;
            Order order = mock(Order.class);
            given(order.getId()).willReturn(orderId);
            doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "본인의 주문만 조회/취소할 수 있습니다."))
                    .when(order).validateOwner(userId);

            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderFacade.cancelOrder(userId, orderId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 주문만 조회/취소할 수 있습니다.");

            verify(optionRepository, never()).findByIdWithLock(anyLong());
            verify(orderRepository, never()).save(any());
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
