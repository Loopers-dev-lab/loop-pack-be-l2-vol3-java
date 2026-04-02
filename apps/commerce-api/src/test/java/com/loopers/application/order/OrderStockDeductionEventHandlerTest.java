package com.loopers.application.order;

import com.loopers.application.order.queue.OrderAdmissionApplicationService;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStockDeductionEventHandlerTest {

    private static final String MEMBER_ID = "stockmember";
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private OrderApplicationService orderApplicationService;

    @Mock
    private ProductStockApplicationService productStockApplicationService;

    @Mock
    private OrderAdmissionApplicationService orderAdmissionApplicationService;

    @InjectMocks
    private OrderStockDeductionEventHandler orderStockDeductionEventHandler;

    @Test
    @DisplayName("결제 성공이 아닌 상태 변경 이벤트는 무시한다")
    void handleSkipsWhenPaymentNotSucceeded() {
        orderStockDeductionEventHandler.handle(
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.FAILED)
        );

        verifyNoInteractions(orderApplicationService, productStockApplicationService, orderAdmissionApplicationService);
    }

    @Test
    @DisplayName("이미 취소된 주문은 재고 차감과 토큰 종료를 수행하지 않는다")
    void handleSkipsCancelledOrder() {
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(cancelledOrder());

        orderStockDeductionEventHandler.handle(
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.SUCCEEDED)
        );

        verify(productStockApplicationService, never()).decreaseStockForOrderItems(any());
        verify(orderApplicationService, never()).markStockDeducted(any());
        verifyNoInteractions(orderAdmissionApplicationService);
    }

    @Test
    @DisplayName("이미 재고 차감된 주문은 중복 차감을 수행하지 않는다")
    void handleSkipsAlreadyDeductedOrder() {
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(stockDeductedOrder());

        orderStockDeductionEventHandler.handle(
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.SUCCEEDED)
        );

        verify(productStockApplicationService, never()).decreaseStockForOrderItems(any());
        verify(orderApplicationService, never()).markStockDeducted(any());
        verifyNoInteractions(orderAdmissionApplicationService);
    }

    @Test
    @DisplayName("결제 성공 후 정상 주문이면 재고 차감과 입장 완료를 수행한다")
    void handleDeductsStockAndCompletesAdmission() {
        Order order = orderedOrder();
        when(orderApplicationService.getByIdForSystem(ORDER_ID)).thenReturn(order);

        orderStockDeductionEventHandler.handle(
                new PaymentStatusChangedEvent(MEMBER_ID, ORDER_ID, PaymentStatus.REQUESTED, PaymentStatus.SUCCEEDED)
        );

        verify(productStockApplicationService).decreaseStockForOrderItems(order.items());
        verify(orderApplicationService).markStockDeducted(ORDER_ID);
        verify(orderAdmissionApplicationService).completeAdmission(MEMBER_ID);
    }

    private Order orderedOrder() {
        return new Order(
                ORDER_ID,
                MEMBER_ID,
                "ORD-STOCK-1",
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                12000,
                null,
                0,
                List.of(new OrderItem(UUID.randomUUID(), 1, "상품", 12000, "브랜드")),
                null,
                null
        );
    }

    private Order cancelledOrder() {
        return new Order(
                ORDER_ID,
                MEMBER_ID,
                "ORD-STOCK-2",
                ZonedDateTime.now(),
                OrderStatus.CANCELLED,
                12000,
                null,
                0,
                List.of(new OrderItem(UUID.randomUUID(), 1, "상품", 12000, "브랜드")),
                ZonedDateTime.now(),
                null
        );
    }

    private Order stockDeductedOrder() {
        return new Order(
                ORDER_ID,
                MEMBER_ID,
                "ORD-STOCK-3",
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                12000,
                null,
                0,
                List.of(new OrderItem(UUID.randomUUID(), 1, "상품", 12000, "브랜드")),
                null,
                ZonedDateTime.now()
        );
    }
}
