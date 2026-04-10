package com.loopers.application.event;

import com.loopers.domain.like.LikeAddedEvent;
import com.loopers.domain.like.LikeRemovedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventHandler {

    private final ProductService productService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleLikeAdded(LikeAddedEvent event) {
        productService.increaseLikes(event.productId());
        log.info("[UserBehavior] 좋아요 추가 - userId={}, productId={}", event.userId(), event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleLikeRemoved(LikeRemovedEvent event) {
        productService.decreaseLikes(event.productId());
        log.info("[UserBehavior] 좋아요 취소 - userId={}, productId={}", event.userId(), event.productId());
    }
}
