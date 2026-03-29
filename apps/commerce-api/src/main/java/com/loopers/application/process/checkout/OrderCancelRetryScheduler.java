package com.loopers.application.process.checkout;

import com.loopers.application.process.checkout.event.OrderCancelCompensationRequestedEvent;
import com.loopers.domain.order.OrderCancelSagaProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelRetryScheduler {

    private final OrderCancelSagaProgressRepository progressRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Scheduled(fixedDelayString = "${loopers.order.cancel.retry-delay-ms:5000}")
    public void retry() {
        for (var progress : progressRepository.findRetryCandidates(3)) {
            if (!progress.canRetry()) {
                continue;
            }
            log.info("order_cancel_retry orderId={} retryCount={} lastError={}", progress.orderId(), progress.retryCount(), progress.lastError());
            applicationEventPublisher.publishEvent(new OrderCancelCompensationRequestedEvent(progress.orderId()));
        }
    }
}
