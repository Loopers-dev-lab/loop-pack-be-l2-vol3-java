package com.loopers.application.like;

import com.loopers.domain.like.event.LikeRemovedEvent;
import com.loopers.domain.like.event.LikedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class LikeEventHandler {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLiked(LikedEvent event) {
        log.info("LikedEvent received: productDbId={}", event.productDbId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeRemoved(LikeRemovedEvent event) {
        log.info("LikeRemovedEvent received: productDbId={}", event.productDbId());
    }
}
