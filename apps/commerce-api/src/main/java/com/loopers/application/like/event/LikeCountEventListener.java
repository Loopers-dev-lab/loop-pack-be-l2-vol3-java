package com.loopers.application.like.event;

import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountEventListener {

    private final ProductDomainService productDomainService;
    private final PlatformTransactionManager transactionManager;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeEvent(LikeEvent event) {
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.executeWithoutResult(status -> {
                if (event.action() == LikeEvent.LikeAction.LIKED) {
                    productDomainService.incrementLikeCount(event.productId());
                } else {
                    productDomainService.decrementLikeCount(event.productId());
                }
            });
        } catch (Exception e) {
            log.warn("[LikeCount 업데이트 실패] productId={}, action={}, error={}",
                event.productId(), event.action(), e.getMessage());
        }
    }
}
