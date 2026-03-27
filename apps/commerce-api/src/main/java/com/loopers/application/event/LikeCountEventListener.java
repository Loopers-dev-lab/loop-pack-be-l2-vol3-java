package com.loopers.application.event;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.event.LikeToggledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountEventListener {
    private final ProductAppService productAppService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handle(LikeToggledEvent event) {
        if (event.liked()) {
            productAppService.increaseLikeCount(event.productId());
        } else {
            productAppService.decreaseLikeCount(event.productId());
        }
        log.info("좋아요 카운트 반영: productId={}, liked={}", event.productId(), event.liked());
    }
}
