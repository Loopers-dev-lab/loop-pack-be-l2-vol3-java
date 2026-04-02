package com.loopers.application.like;

import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.UnlikedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventListener {

    private final ProductService productService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLiked(LikedEvent event) {
        log.info("좋아요 이벤트 수신. productId={}", event.getProductId());
        productService.increaseLikeCount(event.getProductId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUnliked(UnlikedEvent event) {
        log.info("좋아요 취소 이벤트 수신. productId={}", event.getProductId());
        productService.decreaseLikeCount(event.getProductId());
    }
}
