package com.loopers.application.like;

import com.loopers.domain.like.LikeCancelledEvent;
import com.loopers.domain.like.LikeCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 이벤트 리스너.
 *
 * AFTER_COMMIT을 사용하는 이유:
 * - 좋아요 저장이 롤백되면 카운트 증가도 실행하지 않아야 함
 * - 카운트 증가 실패가 좋아요 저장에 영향을 주지 않음 (Eventual Consistency)
 *
 * 예외를 catch하는 이유:
 * - AFTER_COMMIT은 같은 스레드에서 동기 실행되므로 예외가 호출자에게 전파됨
 * - 카운트 증가 실패가 사용자 응답을 500으로 만들면 안 됨
 *
 * LikeCountHandler(별도 빈)에 위임하는 이유:
 * - AFTER_COMMIT은 트랜잭션 밖에서 실행되므로 새 트랜잭션이 필요
 * - 별도 빈의 @Transactional이 프록시를 통해 새 트랜잭션을 생성
 * (PaymentEventListener → PaymentResultHandler와 동일한 패턴)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventListener {

    private final LikeCountHandler likeCountHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCreated(LikeCreatedEvent event) {
        try {
            likeCountHandler.increaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 수 증가 실패 (Eventual Consistency로 보정 예정): productId={}", event.productId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCancelled(LikeCancelledEvent event) {
        try {
            likeCountHandler.decreaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 수 감소 실패 (Eventual Consistency로 보정 예정): productId={}", event.productId(), e);
        }
    }
}
