package com.loopers.application.order;

import com.loopers.application.cart.CartAppService;
import com.loopers.application.coupon.CouponAppService;
import com.loopers.application.product.ProductAppService;
import com.loopers.application.queue.TokenService;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.product.Option;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OrderAppService 단위 테스트")
class OrderAppServiceTest {

    private OrderAppService orderAppService;
    private OrderRepository orderRepository;
    private ProductAppService productAppService;
    private CouponAppService couponAppService;
    private CartAppService cartAppService;
    private ApplicationEventPublisher eventPublisher;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productAppService = mock(ProductAppService.class);
        couponAppService = mock(CouponAppService.class);
        cartAppService = mock(CartAppService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        tokenService = mock(TokenService.class);
        orderAppService = new OrderAppService(orderRepository, productAppService, couponAppService, cartAppService, eventPublisher, tokenService);
    }

    private OrderItem createTestOrderItem() {
        return OrderItem.of(1L, "테스트 상품", "기본 옵션", Money.of(10000L), 2);
    }

    private Order createPendingOrder() {
        Order order = mock(Order.class);
        given(order.getId()).willReturn(1L);
        given(order.getUserId()).willReturn(1L);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getOrderItems()).willReturn(List.of(createTestOrderItem()));
        return order;
    }

    @Nested
    @DisplayName("주문 조회")
    class GetByIdTest {

        @Test
        @DisplayName("ID로 주문을 조회할 수 있다")
        void getById_found() {
            // given
            Order order = createPendingOrder();
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when
            Order result = orderAppService.getById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 예외가 발생한다")
        void getById_notFound() {
            // given
            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderAppService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("주문 상태 전이 - 결제")
    class PayTest {

        @Test
        @DisplayName("PENDING 상태에서 결제할 수 있다")
        void pay_success() {
            // given
            Order order = createPendingOrder();
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            given(order.getStatus()).willReturn(OrderStatus.PAID);

            // when
            Order result = orderAppService.pay(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 결제하면 예외가 발생한다")
        void pay_invalidState() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "결제 대기 상태에서만 결제할 수 있습니다."))
                    .when(order).pay();

            // when & then
            assertThatThrownBy(() -> orderAppService.pay(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 대기 상태에서만 결제할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("주문 상태 전이 - 준비")
    class PrepareTest {

        @Test
        @DisplayName("PAID 상태에서 준비할 수 있다")
        void prepare_success() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(order.getStatus()).willReturn(OrderStatus.PREPARING);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));

            // when
            Order result = orderAppService.prepare(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("PAID가 아닌 상태에서 준비하면 예외가 발생한다")
        void prepare_invalidState() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "결제 완료 상태에서만 준비할 수 있습니다."))
                    .when(order).prepare();

            // when & then
            assertThatThrownBy(() -> orderAppService.prepare(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 완료 상태에서만 준비할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("주문 상태 전이 - 배송")
    class ShipTest {

        @Test
        @DisplayName("PREPARING 상태에서 배송을 시작할 수 있다")
        void ship_success() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(order.getStatus()).willReturn(OrderStatus.SHIPPED);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));

            // when
            Order result = orderAppService.ship(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("PREPARING이 아닌 상태에서 배송하면 예외가 발생한다")
        void ship_invalidState() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "준비 완료 상태에서만 배송을 시작할 수 있습니다."))
                    .when(order).ship();

            // when & then
            assertThatThrownBy(() -> orderAppService.ship(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("준비 완료 상태에서만 배송을 시작할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("주문 상태 전이 - 배송 완료")
    class DeliverTest {

        @Test
        @DisplayName("SHIPPED 상태에서 배송 완료할 수 있다")
        void deliver_success() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(order.getStatus()).willReturn(OrderStatus.DELIVERED);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));

            // when
            Order result = orderAppService.deliver(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("SHIPPED가 아닌 상태에서 배송 완료하면 예외가 발생한다")
        void deliver_invalidState() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "배송 중 상태에서만 배송 완료 처리할 수 있습니다."))
                    .when(order).deliver();

            // when & then
            assertThatThrownBy(() -> orderAppService.deliver(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("배송 중 상태에서만 배송 완료 처리할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelTest {

        @Test
        @DisplayName("PENDING 상태에서 취소할 수 있다")
        void cancel_fromPending() {
            // given
            Order order = createPendingOrder();
            given(order.getIssuedCouponId()).willReturn(null);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            given(order.getStatus()).willReturn(OrderStatus.CANCELED);

            Option option = mock(Option.class);
            given(productAppService.getOptionByIdWithLock(1L)).willReturn(option);

            // when
            Order result = orderAppService.cancel(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("PAID 상태에서 취소할 수 있다")
        void cancel_fromPaid() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(order.getStatus()).willReturn(OrderStatus.CANCELED);
            given(order.getIssuedCouponId()).willReturn(null);
            given(order.getOrderItems()).willReturn(List.of(createTestOrderItem()));
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));

            Option option = mock(Option.class);
            given(productAppService.getOptionByIdWithLock(1L)).willReturn(option);

            // when
            Order result = orderAppService.cancel(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("PREPARING 상태에서는 취소할 수 없다")
        void cancel_fromPreparing_fails() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."))
                    .when(order).cancel();

            // when & then
            assertThatThrownBy(() -> orderAppService.cancel(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("취소할 수 없는 주문 상태입니다.");
        }

        @Test
        @DisplayName("SHIPPED 상태에서는 취소할 수 없다")
        void cancel_fromShipped_fails() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."))
                    .when(order).cancel();

            // when & then
            assertThatThrownBy(() -> orderAppService.cancel(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("취소할 수 없는 주문 상태입니다.");
        }

        @Test
        @DisplayName("취소 실패 시 쿠폰/재고 Lock을 획득하지 않는다")
        void cancel_failFast_noLockAcquired() {
            // given
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."))
                    .when(order).cancel();

            // when
            assertThatThrownBy(() -> orderAppService.cancel(1L))
                    .isInstanceOf(CoreException.class);

            // then - 복원 로직(Lock 획득)이 호출되지 않았음을 검증
            org.mockito.Mockito.verifyNoInteractions(couponAppService);
            org.mockito.Mockito.verifyNoInteractions(productAppService);
        }

        @Test
        @DisplayName("취소 시 쿠폰과 재고가 복원된다")
        void cancel_restoresCouponAndStock() {
            // given
            OrderItem orderItem = createTestOrderItem();
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(order.getIssuedCouponId()).willReturn(10L);
            given(order.getOrderItems()).willReturn(List.of(orderItem));
            given(orderRepository.findByIdWithLock(1L)).willReturn(Optional.of(order));

            IssuedCoupon issuedCoupon = mock(IssuedCoupon.class);
            given(couponAppService.getIssuedCouponByIdWithLock(10L)).willReturn(issuedCoupon);

            Option option = mock(Option.class);
            given(productAppService.getOptionByIdWithLock(1L)).willReturn(option);

            // when
            orderAppService.cancel(1L);

            // then
            verify(issuedCoupon).restore();
            verify(option).increaseStock(2);
            verify(order).cancel();
        }
    }

    @Nested
    @DisplayName("사용자별 주문 조회")
    class GetByUserIdTest {

        @Test
        @DisplayName("사용자의 주문 목록을 반환한다")
        void getByUserId() {
            // given
            Long userId = 1L;
            Order order = createPendingOrder();
            given(orderRepository.findByUserId(userId)).willReturn(List.of(order));

            // when
            List<Order> result = orderAppService.getByUserId(userId);

            // then
            assertThat(result).hasSize(1);
        }
    }
}
