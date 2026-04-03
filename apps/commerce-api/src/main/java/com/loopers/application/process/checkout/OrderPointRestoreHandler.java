package com.loopers.application.process.checkout;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.point.PointApplicationService;
import com.loopers.application.process.checkout.event.OrderCancelCompensationRequestedEvent;
import com.loopers.application.process.checkout.event.OrderCancelCompensationStepCompletedEvent;
import com.loopers.domain.order.OrderCancelSagaProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPointRestoreHandler {

    private final OrderApplicationService orderApplicationService;
    private final PointApplicationService pointApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderCancelSagaProgressRepository progressRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCancelCompensationRequestedEvent event) {
        var order = orderApplicationService.getByIdForSystem(event.orderId());
        try {
            if (order.usedPointAmount() > 0) {
                pointApplicationService.restore(order.memberId(), order.usedPointAmount());
            }
            applicationEventPublisher.publishEvent(new OrderCancelCompensationStepCompletedEvent(order.id(), OrderCancelCompensationStepCompletedEvent.StepType.POINT));
        } catch (Exception e) {
            progressRepository.findByOrderId(order.id())
                    .ifPresent(progress -> progressRepository.save(progress.markError(e.getMessage())));
            throw e;
        }
    }
}
