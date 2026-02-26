package com.loopers.application.order;

import com.loopers.domain.model.order.*;
import com.loopers.domain.model.product.Price;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.model.product.ProductName;
import com.loopers.domain.model.product.Stock;
import com.loopers.domain.model.product.ProductData;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.OrderRepository;
import com.loopers.domain.repository.ProductRepository;
import com.loopers.domain.model.common.DomainEventPublisher;
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
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private DomainEventPublisher eventPublisher;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        service = new OrderService(orderRepository, productRepository, eventPublisher);
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("주문 생성 성공")
        void createOrder_success() {
            // given
            UserId userId = UserId.of("test1234");
            Product product = createProduct(1L, 50000, 100);
            when(productRepository.findActiveByIdWithLock(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenReturn(product);

            var command = new CreateOrderUseCase.OrderCommand(
                    List.of(new CreateOrderUseCase.OrderItemCommand(1L, 2)),
                    "홍길동",
                    "서울시 강남구",
                    "문 앞에 놓아주세요",
                    "CARD",
                    LocalDate.now().plusDays(3)
            );

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> service.createOrder(userId, command));

            verify(productRepository).save(any(Product.class));
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품으로 주문시 예외")
        void createOrder_fail_productNotFound() {
            // given
            UserId userId = UserId.of("test1234");
            when(productRepository.findActiveByIdWithLock(999L)).thenReturn(Optional.empty());

            var command = new CreateOrderUseCase.OrderCommand(
                    List.of(new CreateOrderUseCase.OrderItemCommand(999L, 1)),
                    "홍길동", "서울시", "요청사항", "CARD", LocalDate.now()
            );

            // when & then
            assertThatThrownBy(() -> service.createOrder(userId, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("재고 부족시 예외")
        void createOrder_fail_insufficientStock() {
            // given
            UserId userId = UserId.of("test1234");
            Product product = createProduct(1L, 50000, 1);
            when(productRepository.findActiveByIdWithLock(1L)).thenReturn(Optional.of(product));

            var command = new CreateOrderUseCase.OrderCommand(
                    List.of(new CreateOrderUseCase.OrderItemCommand(1L, 100)),
                    "홍길동", "서울시", "요청사항", "CARD", LocalDate.now()
            );

            // when & then
            assertThatThrownBy(() -> service.createOrder(userId, command))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrder {

        @Test
        @DisplayName("주문 취소 성공 - 이벤트 발행")
        void cancelOrder_success() {
            // given
            UserId userId = UserId.of("test1234");
            Order order = createOrder(1L, userId, OrderStatus.PAYMENT_COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when
            service.cancelOrder(userId, 1L);

            // then
            verify(orderRepository).save(any(Order.class));
            verify(eventPublisher).publishEvents(any(Order.class));
        }

        @Test
        @DisplayName("다른 사용자 주문 취소시 예외")
        void cancelOrder_fail_notOwner() {
            // given
            UserId userId = UserId.of("test1234");
            UserId otherUser = UserId.of("other123");
            Order order = createOrder(1L, otherUser, OrderStatus.PAYMENT_COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> service.cancelOrder(userId, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("배송중 주문 취소시 예외")
        void cancelOrder_fail_shipping() {
            // given
            UserId userId = UserId.of("test1234");
            Order order = createOrder(1L, userId, OrderStatus.SHIPPING);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> service.cancelOrder(userId, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("주문을 취소할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("배송지 변경")
    class UpdateDeliveryAddress {

        @Test
        @DisplayName("배송지 변경 성공")
        void updateDeliveryAddress_success() {
            // given
            UserId userId = UserId.of("test1234");
            Order order = createOrder(1L, userId, OrderStatus.PAYMENT_COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> service.updateDeliveryAddress(userId, 1L, "새로운 주소"));

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("다른 사용자 주문 배송지 변경시 예외")
        void updateDeliveryAddress_fail_notOwner() {
            // given
            UserId userId = UserId.of("test1234");
            UserId otherUser = UserId.of("other123");
            Order order = createOrder(1L, otherUser, OrderStatus.PAYMENT_COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> service.updateDeliveryAddress(userId, 1L, "새 주소"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("배송중 주문 배송지 변경시 예외")
        void updateDeliveryAddress_fail_shipping() {
            // given
            UserId userId = UserId.of("test1234");
            Order order = createOrder(1L, userId, OrderStatus.SHIPPING);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> service.updateDeliveryAddress(userId, 1L, "새 주소"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("배송지를 변경할 수 없습니다");
        }
    }

    private Product createProduct(Long id, int price, int stock) {
        return Product.reconstitute(new ProductData(id, 1L, ProductName.of("상품" + id), Price.of(price),
                null, Stock.of(stock), 0, "설명",
                LocalDateTime.now(), LocalDateTime.now(), null));
    }

    private Order createOrder(Long id, UserId userId, OrderStatus status) {
        List<OrderItem> items = List.of(
                OrderItem.reconstitute(1L, 1L, 2, Money.of(50000))
        );
        DeliveryInfo deliveryInfo = DeliveryInfo.of(
                "홍길동", "서울시 강남구",
                "문 앞에 놓아주세요", LocalDate.now().plusDays(3));
        OrderAmount orderAmount = OrderAmount.reconstitute(
                PaymentMethod.CARD, Money.of(100000), Money.zero(), Money.of(100000));
        return Order.reconstitute(new OrderData(id, userId, items, null,
                deliveryInfo, orderAmount, status,
                LocalDateTime.now(), LocalDateTime.now()));
    }
}
