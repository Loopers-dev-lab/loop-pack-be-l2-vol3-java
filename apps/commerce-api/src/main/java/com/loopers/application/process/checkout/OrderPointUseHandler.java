package com.loopers.application.process.checkout;

import com.loopers.application.point.PointApplicationService;
import com.loopers.application.process.checkout.event.OrderCouponAppliedEvent;
import com.loopers.application.process.checkout.event.OrderPointUsedEvent;
import com.loopers.domain.order.OrderCreateSagaProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPointUseHandler {

    private final OrderCreateSagaProgressRepository progressRepository;
    private final PointApplicationService pointApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCouponAppliedEvent event) {
        var progress = progressRepository.findByOrderId(event.orderId()).orElseThrow();
        if (progress.pointDone()) {
            applicationEventPublisher.publishEvent(new OrderPointUsedEvent(event.orderId()));
            return;
        }
        if (progress.requestedPointAmount() > 0) {
            pointApplicationService.use(progress.memberId(), progress.requestedPointAmount());
        }
        progressRepository.save(progress.markPointDone());
        applicationEventPublisher.publishEvent(new OrderPointUsedEvent(event.orderId()));
    }
}
