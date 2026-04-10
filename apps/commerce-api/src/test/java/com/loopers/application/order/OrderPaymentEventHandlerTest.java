package com.loopers.application.order;

import com.loopers.application.process.checkout.event.OrderPaymentCancelRequestEvent;
import com.loopers.application.process.checkout.event.OrderPaymentRequestEvent;
import com.loopers.application.payment.PaymentCancelApplicationService;
import com.loopers.application.payment.PaymentStartApplicationService;
import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventHandlerTest {

    private static final String MEMBER_ID = "ordermember";
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CARD_NO = "1234-5678-1234-5678";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    @Mock
    private PaymentStartApplicationService paymentStartApplicationService;

    @Mock
    private PaymentCancelApplicationService paymentCancelApplicationService;

    @Mock
    private OrderApplicationService orderApplicationService;

    @InjectMocks
    private OrderPaymentEventHandler orderPaymentEventHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderPaymentEventHandler, "callbackUrl", CALLBACK_URL);
    }

    @Test
    @DisplayName("유효한 주문 결제 요청 이벤트면 결제 시작을 위임한다")
    void handleOrderPaymentRequestStartsPayment() {
        Order order = orderedOrder();
        OrderPaymentRequestEvent event =
                new OrderPaymentRequestEvent(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 15000);
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(order);

        orderPaymentEventHandler.handleOrderPaymentRequest(event);

        verify(paymentStartApplicationService).start(
                new StartPaymentCommand(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 15000, CALLBACK_URL)
        );
    }

    @Test
    @DisplayName("결제 금액이 0 이하인 요청 이벤트는 무시한다")
    void handleOrderPaymentRequestSkipsWhenAmountIsNotPositive() {
        OrderPaymentRequestEvent event =
                new OrderPaymentRequestEvent(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 0);

        orderPaymentEventHandler.handleOrderPaymentRequest(event);

        verifyNoInteractions(orderApplicationService, paymentStartApplicationService);
    }

    @Test
    @DisplayName("이미 취소된 주문의 결제 요청 이벤트는 무시한다")
    void handleOrderPaymentRequestSkipsCancelledOrder() {
        OrderPaymentRequestEvent event =
                new OrderPaymentRequestEvent(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 15000);
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(cancelledOrder());

        orderPaymentEventHandler.handleOrderPaymentRequest(event);

        verify(paymentStartApplicationService, never()).start(any());
    }

    @Test
    @DisplayName("결제 취소 이벤트에서 결제 내역이 없으면 예외를 삼킨다")
    void handleOrderPaymentCancelRequestIgnoresNotFound() {
        when(paymentCancelApplicationService.cancel(new CancelPaymentCommand(MEMBER_ID, ORDER_ID)))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "missing"));

        assertThatCode(() -> orderPaymentEventHandler.handleOrderPaymentCancelRequest(
                new OrderPaymentCancelRequestEvent(MEMBER_ID, ORDER_ID)
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("결제 취소 이벤트에서 NOT_FOUND 외 예외는 그대로 전파한다")
    void handleOrderPaymentCancelRequestRethrowsUnexpectedException() {
        when(paymentCancelApplicationService.cancel(new CancelPaymentCommand(MEMBER_ID, ORDER_ID)))
                .thenThrow(new CoreException(ErrorType.INTERNAL_ERROR, "boom"));

        assertThatThrownBy(() -> orderPaymentEventHandler.handleOrderPaymentCancelRequest(
                new OrderPaymentCancelRequestEvent(MEMBER_ID, ORDER_ID)
        )).isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("결제 성공이 아닌 상태 변경 이벤트는 무시한다")
    void handlePaymentStatusChangedSkipsWhenPaymentNotSucceeded() {
        PaymentStatusChangedEvent event =
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.FAILED);

        orderPaymentEventHandler.handlePaymentStatusChanged(event);

        verifyNoInteractions(orderApplicationService, paymentCancelApplicationService);
    }

    @Test
    @DisplayName("취소되지 않은 주문의 결제 성공 이벤트는 취소를 요청하지 않는다")
    void handlePaymentStatusChangedSkipsWhenOrderIsActive() {
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(orderedOrder());

        orderPaymentEventHandler.handlePaymentStatusChanged(
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.SUCCEEDED)
        );

        verify(paymentCancelApplicationService, never()).cancel(any());
    }

    @Test
    @DisplayName("취소된 주문의 결제 성공 이벤트면 결제 취소를 요청한다")
    void handlePaymentStatusChangedCancelsSucceededPaymentForCancelledOrder() {
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(cancelledOrder());

        orderPaymentEventHandler.handlePaymentStatusChanged(
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.SUCCEEDED)
        );

        verify(paymentCancelApplicationService).cancel(new CancelPaymentCommand(MEMBER_ID, ORDER_ID));
    }

    private Order orderedOrder() {
        return new Order(
                ORDER_ID,
                MEMBER_ID,
                "ORD-001",
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                15000,
                null,
                0,
                List.of(new OrderItem(UUID.randomUUID(), 1, "상품", 15000, "브랜드")),
                null,
                null
        );
    }

    private Order cancelledOrder() {
        return new Order(
                ORDER_ID,
                MEMBER_ID,
                "ORD-002",
                ZonedDateTime.now(),
                OrderStatus.CANCELLED,
                15000,
                null,
                0,
                List.of(new OrderItem(UUID.randomUUID(), 1, "상품", 15000, "브랜드")),
                ZonedDateTime.now(),
                null
        );
    }
}
