package com.loopers.application.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;
import static org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductLikeEventHandler {

    private final ProductService productService;
    private final ProductCacheService productCacheService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void saveToOutboxOnLike(ProductLikedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent(
                    "PRODUCT", event.productId(), "PRODUCT_LIKED", payload
            ));
        } catch (Exception e) {
            log.error("Outbox 저장 실패 (좋아요): {}", event, e);
            throw new RuntimeException(e);
        }
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void saveToOutboxOnUnlike(ProductUnlikedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent(
                    "PRODUCT", event.productId(), "PRODUCT_UNLIKED", payload
            ));
        } catch (Exception e) {
            log.error("Outbox 저장 실패 (좋아요 취소): {}", event, e);
            throw new RuntimeException(e);
        }
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleProductLiked(ProductLikedEvent event) {
        log.info("좋아요 이벤트 처리: memberId={}, productId={}", event.memberId(), event.productId());
        productService.increaseLikeCount(event.productId());
        productCacheService.evictProductDetail(event.productId());
        productCacheService.evictProductList();
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        log.info("좋아요 취소 처리: memberId={}, productId={}", event.memberId(), event.productId());
        productService.decreaseLikeCount(event.productId());
        productCacheService.evictProductDetail(event.productId());
        productCacheService.evictProductList();
    }
}
