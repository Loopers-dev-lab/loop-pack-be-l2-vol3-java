package com.loopers.application.productlike;

import com.loopers.domain.event.ProductLikedEvent;
import com.loopers.domain.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeEventListener {

    private final ProductService productService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        try {
            log.info("좋아요 등록 이벤트 수신: userId={}, productId={}", event.userId(), event.productId());
            productService.increaseLikes(event.productId());
        } catch (Exception e) {
            log.error("좋아요 등록 이벤트 처리 실패: userId={}, productId={}", event.userId(), event.productId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        try {
            log.info("좋아요 취소 이벤트 수신: userId={}, productId={}", event.userId(), event.productId());
            productService.decreaseLikes(event.productId());
        } catch (Exception e) {
            log.error("좋아요 취소 이벤트 처리 실패: userId={}, productId={}", event.userId(), event.productId(), e);
        }
    }
}
