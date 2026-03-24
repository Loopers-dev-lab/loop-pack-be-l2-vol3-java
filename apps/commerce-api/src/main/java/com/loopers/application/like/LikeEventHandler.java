package com.loopers.application.like;

import com.loopers.domain.like.event.LikeRemovedEvent;
import com.loopers.domain.like.event.LikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LikeEventHandler {

    private final LikeEventPublisher likeEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLiked(LikedEvent event) {
        likeEventPublisher.publish(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeRemoved(LikeRemovedEvent event) {
        likeEventPublisher.publish(event);
    }
}
