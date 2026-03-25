package com.loopers.application.like;

import com.loopers.domain.like.LikeCancelledEvent;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
 * @Async를 사용하는 이유:
 * - AFTER_COMMIT은 동기 실행 → 원래 커넥션이 cleanup 전이라 REQUIRES_NEW + 별도 빈 필요
 * - @Async로 별도 스레드에서 실행하면 TX 컨텍스트가 없으므로 ProductService의 @Transactional이 정상 동작
 * - 커넥션 동시 점유 문제 해소 + 응답 시간 단축
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventListener {

    private final ProductService productService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCreated(LikeCreatedEvent event) {
        try {
            productService.increaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 수 증가 실패 (Eventual Consistency로 보정 예정): productId={}", event.productId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCancelled(LikeCancelledEvent event) {
        try {
            productService.decreaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 수 감소 실패 (Eventual Consistency로 보정 예정): productId={}", event.productId(), e);
        }
    }
}
