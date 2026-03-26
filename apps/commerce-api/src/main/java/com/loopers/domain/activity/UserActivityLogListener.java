package com.loopers.domain.activity;

import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityLogListener {

    @Async
    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("[유저행동] 상품조회 userId={}, productId={}, userAgent={}",
            event.userId(), event.productId(), event.userAgent());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCreated(LikeCreatedEvent event) {
        log.info("[유저행동] 좋아요 userId={}, productId={}",
            event.userId(), event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[유저행동] 주문생성 userId={}, orderId={}, amount={}",
            event.memberId(), event.orderId(), event.amount());
    }
}