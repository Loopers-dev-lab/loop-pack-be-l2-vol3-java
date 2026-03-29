package com.loopers.application.process.checkout;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.application.point.PointApplicationService;
import com.loopers.domain.order.OrderCreateSagaProgressRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderCreateCompensationHandler {

    private final OrderApplicationService orderApplicationService;
    private final OrderCreateSagaProgressRepository progressRepository;
    private final CouponApplicationService couponApplicationService;
    private final PointApplicationService pointApplicationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentStatusChangedEvent event) {
        if (event.afterStatus() == PaymentStatus.SUCCEEDED) {
            progressRepository.findByOrderId(event.orderId())
                    .ifPresent(progress -> progressRepository.save(progress.markCompleted()));
            return;
        }
        if (event.afterStatus() != PaymentStatus.FAILED) {
            return;
        }

        var order = orderApplicationService.getByIdForSystem(event.orderId());
        if (order.couponId() != null) {
            couponApplicationService.cancelUse(order.couponId(), order.memberId());
        }
        if (order.usedPointAmount() > 0) {
            pointApplicationService.restore(order.memberId(), order.usedPointAmount());
        }
        progressRepository.findByOrderId(event.orderId())
                .ifPresent(progress -> progressRepository.save(progress.markCompensated().markError("payment_failed")));
    }
}
