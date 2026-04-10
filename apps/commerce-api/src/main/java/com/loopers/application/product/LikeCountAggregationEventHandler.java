package com.loopers.application.product;

import com.loopers.application.like.event.LikeCancelledEvent;
import com.loopers.application.like.event.LikeRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountAggregationEventHandler {

    private final ProductLikeAplicationService productLikeApplicationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeRegisteredEvent event) {
        try {
            productLikeApplicationService.increaseLikeCount(event.productId());
        } catch (Exception e) {
            log.warn("like_count_aggregation_failed action=LIKE_REGISTER memberId={} productId={}", event.memberId(), event.productId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeCancelledEvent event) {
        try {
            productLikeApplicationService.decreaseLikeCountIfPresent(event.productId());
        } catch (Exception e) {
            log.warn("like_count_aggregation_failed action=LIKE_CANCEL memberId={} productId={}", event.memberId(), event.productId(), e);
        }
    }
}
