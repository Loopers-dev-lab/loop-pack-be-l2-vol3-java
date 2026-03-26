package com.loopers.interfaces.event.useractivity;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.product.ProductEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 유저 행동을 로깅하는 이벤트 리스너.
 *
 * <p>기존 도메인 이벤트를 수신하여 구조화된 로그를 남긴다.
 * AFTER_COMMIT 단계에서 동작하여 로깅 실패가 비즈니스 트랜잭션에 영향을 주지 않는다. 추후 저장소/파이프라인 변경 시 Port를 추출하여 교체한다.</p>
 */
@Slf4j
@Component
public class UserActivityEventListener {

    @Async
    @EventListener
    public void handle(ProductEvent.ProductViewed event) {
        log.info("[USER_ACTIVITY] type=PRODUCT_VIEWED, productId={}", event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeEvent.Liked event) {
        log.info("[USER_ACTIVITY] type=LIKED, productId={}", event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeEvent.Unliked event) {
        log.info("[USER_ACTIVITY] type=UNLIKED, productId={}", event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderEvent.OrderCompleted event) {
        log.info("[USER_ACTIVITY] type=ORDER_COMPLETED, orderId={}", event.orderId());
    }
}
