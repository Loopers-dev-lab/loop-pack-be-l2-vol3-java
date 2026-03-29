package com.loopers.application.order.event;

import com.loopers.application.payment.PaymentTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class OrderEventHandlerTest {

    private PaymentTxService paymentTxService;
    private OrderEventHandler handler;

    @BeforeEach
    void setUp() {
        paymentTxService = mock(PaymentTxService.class);
        handler = new OrderEventHandler(paymentTxService);
    }

    @DisplayName("OrderPlacedEvent 수신 시 예외 없이 정상 처리된다")
    @Test
    void handleOrderPlaced_success() {
        OrderPlacedEvent event = new OrderPlacedEvent(
            1L, 100L,
            List.of(new OrderPlacedEvent.OrderedItem(10L, 2))
        );

        handler.handleOrderPlaced(event);
    }

    @DisplayName("OrderCancelledEvent 수신 시 refundPayment를 호출한다")
    @Test
    void handleOrderCancelled_callsRefundPayment() {
        OrderCancelledEvent event = new OrderCancelledEvent(1L, 100L, List.of(
            new OrderCancelledEvent.CancelledItem(10L, 2)
        ));

        handler.handleOrderCancelled(event);

        verify(paymentTxService).refundPayment(1L);
    }

    @DisplayName("OrderCancelledEvent 처리 중 refundPayment가 실패해도 예외를 전파하지 않는다")
    @Test
    void handleOrderCancelled_refundFails_doesNotThrow() {
        OrderCancelledEvent event = new OrderCancelledEvent(1L, 100L, List.of(
            new OrderCancelledEvent.CancelledItem(10L, 2)
        ));
        doThrow(new RuntimeException("PG 환불 실패")).when(paymentTxService).refundPayment(1L);

        handler.handleOrderCancelled(event);

        verify(paymentTxService).refundPayment(1L);
    }
}
