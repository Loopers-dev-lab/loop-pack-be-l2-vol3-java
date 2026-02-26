package com.loopers.application.order;

import com.loopers.domain.model.order.*;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderQueryServiceTest {

    private OrderRepository orderRepository;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        service = new OrderQueryService(orderRepository);
    }

    @Nested
    @DisplayName("내 주문 목록 조회")
    class GetMyOrders {

        @Test
        @DisplayName("주문 목록 조회 성공")
        void getMyOrders_success() {
            // given
            UserId userId = UserId.of("test1234");
            Order order1 = createOrder(1L, userId, OrderStatus.PAYMENT_COMPLETED);
            Order order2 = createOrder(2L, userId, OrderStatus.SHIPPING);

            when(orderRepository.findAllByUserId(userId)).thenReturn(List.of(order1, order2));

            // when
            var result = service.getMyOrders(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).status()).isEqualTo("PAYMENT_COMPLETED");
        }

        @Test
        @DisplayName("기간 필터 조회 성공")
        void getMyOrders_withDateRange() {
            // given
            UserId userId = UserId.of("test1234");
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 12, 31);

            Order order = createOrder(1L, userId, OrderStatus.PAYMENT_COMPLETED);
            when(orderRepository.findAllByUserIdAndDateRange(eq(userId), any(), any()))
                    .thenReturn(List.of(order));

            // when
            var result = service.getMyOrders(userId, start, end);

            // then
            assertThat(result).hasSize(1);
            verify(orderRepository).findAllByUserIdAndDateRange(eq(userId), any(), any());
        }

        @Test
        @DisplayName("주문 없는 경우 빈 목록")
        void getMyOrders_empty() {
            // given
            UserId userId = UserId.of("test1234");
            when(orderRepository.findAllByUserId(userId)).thenReturn(List.of());

            // when
            var result = service.getMyOrders(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문 상세 조회")
    class GetOrder {

        @Test
        @DisplayName("내 주문 상세 조회 성공")
        void getOrder_success() {
            // given
            UserId userId = UserId.of("test1234");
            Order order = createOrder(1L, userId, OrderStatus.PAYMENT_COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when
            var result = service.getOrder(userId, 1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.receiverName()).isEqualTo("홍길동");
            assertThat(result.status()).isEqualTo("PAYMENT_COMPLETED");
            assertThat(result.items()).hasSize(1);
        }

        @Test
        @DisplayName("다른 사용자 주문 조회시 예외")
        void getOrder_fail_notOwner() {
            // given
            UserId userId = UserId.of("test1234");
            UserId otherUser = UserId.of("other123");
            Order order = createOrder(1L, otherUser, OrderStatus.PAYMENT_COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> service.getOrder(userId, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회시 예외")
        void getOrder_fail_notFound() {
            // given
            UserId userId = UserId.of("test1234");
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getOrder(userId, 999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("관리자 주문 조회")
    class AdminQuery {

        @Test
        @DisplayName("전체 주문 목록 조회")
        void getAllOrders_success() {
            // given
            UserId user1 = UserId.of("user0001");
            UserId user2 = UserId.of("user0002");
            Order order1 = createOrder(1L, user1, OrderStatus.PAYMENT_COMPLETED);
            Order order2 = createOrder(2L, user2, OrderStatus.SHIPPING);

            when(orderRepository.findAll()).thenReturn(List.of(order1, order2));

            // when
            var result = service.getAllOrders();

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("관리자 주문 상세 조회 (userId 검증 없음)")
        void getOrderDetail_success() {
            // given
            UserId userId = UserId.of("test1234");
            Order order = createOrder(1L, userId, OrderStatus.DELIVERED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when
            var result = service.getOrderDetail(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.status()).isEqualTo("DELIVERED");
        }
    }

    private Order createOrder(Long id, UserId userId, OrderStatus status) {
        List<OrderItem> items = List.of(
                OrderItem.reconstitute(1L, 1L, 2, Money.of(50000))
        );
        DeliveryInfo deliveryInfo = DeliveryInfo.of(
                "홍길동", "서울시 강남구",
                "배송 요청", LocalDate.now().plusDays(3));
        OrderAmount orderAmount = OrderAmount.reconstitute(
                PaymentMethod.CARD, Money.of(100000), Money.zero(), Money.of(100000));
        return Order.reconstitute(new OrderData(id, userId, items, null,
                deliveryInfo, orderAmount, status,
                LocalDateTime.now(), LocalDateTime.now()));
    }
}
