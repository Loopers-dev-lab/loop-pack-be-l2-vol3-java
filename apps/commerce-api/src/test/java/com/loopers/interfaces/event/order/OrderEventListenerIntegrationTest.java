package com.loopers.interfaces.event.order;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.order.Cart;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.shared.Money;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.Payment;
import com.loopers.infrastructure.order.persistence.OrderJpaRepository;
import com.loopers.support.BaseIntegrationTest;

class OrderEventListenerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    private Order savedOrder;
    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        Cart cart = new Cart(1L, List.of(
                new Cart.CartItem(1L, "테스트 상품", "https://thumb.png", Money.wons(50000L), 1L)
        ));
        Order order = Order.create("test-order-key", cart, Money.ZERO, null);
        ReflectionTestUtils.setField(order, "status", OrderStatus.ORDERED);
        savedOrder = orderJpaRepository.save(order);

        PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
        savedPayment = paymentService.create(savedOrder, paymentMethod);
        paymentService.confirmPayment(savedPayment.getId(), "txn-event-test");
    }

    @DisplayName("주문 이벤트 리스너가 동작할 때,")
    @Nested
    class OrderEventHandler {

        @DisplayName("결제 성공 이벤트를 수신하면, 주문이 PAID 상태로 변경된다.")
        @Test
        void paysOrder_whenPaymentSucceeded() {
            // act
            paymentService.success(savedPayment.getId(), "결제 승인");

            // assert — 비동기 이벤트 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                Order order = orderService.getById(savedOrder.getId());
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            });
        }

        @DisplayName("결제 실패 이벤트를 수신하면, 주문이 FAILED 상태로 변경된다.")
        @Test
        void failsOrder_whenPaymentFailed() {
            // act
            paymentService.fail(savedPayment.getId(), "잔액 부족");

            // assert — 비동기 이벤트 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                Order order = orderService.getById(savedOrder.getId());
                assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            });
        }
    }
}
