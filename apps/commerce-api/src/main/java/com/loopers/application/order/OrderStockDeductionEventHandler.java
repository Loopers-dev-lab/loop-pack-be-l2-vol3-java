package com.loopers.application.order;

import com.loopers.application.order.queue.OrderAdmissionApplicationService;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderStockDeductionEventHandler {

    private final OrderApplicationService orderApplicationService;
    private final ProductStockApplicationService productStockApplicationService;
    private final OrderAdmissionApplicationService orderAdmissionApplicationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentStatusChangedEvent event) {
        if (event.afterStatus() != PaymentStatus.SUCCEEDED) {
            return;
        }

        Order order = orderApplicationService.getByIdForSystem(event.orderId());
        if (order.isCancelled() || order.isStockDeducted()) {
            return;
        }

        productStockApplicationService.decreaseStockForOrderItems(order.items());
        orderApplicationService.markStockDeducted(order.id());
        orderAdmissionApplicationService.completeAdmission(order.memberId());
    }
}
