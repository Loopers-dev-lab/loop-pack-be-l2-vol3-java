package com.loopers.application.process.checkout;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.command.UseCouponCommand;
import com.loopers.application.process.checkout.event.OrderCouponAppliedEvent;
import com.loopers.application.process.checkout.event.OrderCreateRequestedEvent;
import com.loopers.domain.order.OrderCreateSagaProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderCouponApplyHandler {

    private final OrderCreateSagaProgressRepository progressRepository;
    private final CouponApplicationService couponApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreateRequestedEvent event) {
        var progress = progressRepository.findByOrderId(event.orderId()).orElseThrow();
        if (progress.couponDone()) {
            applicationEventPublisher.publishEvent(new OrderCouponAppliedEvent(event.orderId()));
            return;
        }
        couponApplicationService.use(new UseCouponCommand(progress.couponId(), progress.memberId(), progress.orderAmount()));
        progressRepository.save(progress.markCouponDone());
        applicationEventPublisher.publishEvent(new OrderCouponAppliedEvent(event.orderId()));
    }
}
