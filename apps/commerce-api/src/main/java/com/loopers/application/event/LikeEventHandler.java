package com.loopers.application.event;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 좋아요 이벤트 핸들러.
 *
 * <p>트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되며,
 * 상품의 좋아요 수를 증감시킨다. REQUIRES_NEW 트랜잭션으로 독립 실행된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventHandler {

    private final ProductService productService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * 좋아요 등록 이벤트를 처리한다 (좋아요 수 증가).
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProductLiked(ProductLikedEvent event) {
        try {
            productService.incrementLikeCount(event.productId());
            log.info("[ProductLiked] userId={}, productId={}", event.userId(), event.productId());

            // Kafka direct send: 카탈로그 이벤트 (Outbox 불필요 — 비핵심 지표)
            sendCatalogEvent("PRODUCT_LIKED", event.productId(), event.userId());
        } catch (Exception e) {
            log.error("[ProductLiked] Failed to increment like count. userId={}, productId={}",
                    event.userId(), event.productId(), e);
        }
    }

    /**
     * 좋아요 취소 이벤트를 처리한다 (좋아요 수 감소).
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        try {
            productService.decrementLikeCount(event.productId());
            log.info("[ProductUnliked] userId={}, productId={}", event.userId(), event.productId());

            // Kafka direct send: 카탈로그 이벤트 (Outbox 불필요 — 비핵심 지표)
            sendCatalogEvent("PRODUCT_UNLIKED", event.productId(), event.userId());
        } catch (Exception e) {
            log.error("[ProductUnliked] Failed to decrement like count. userId={}, productId={}",
                    event.userId(), event.productId(), e);
        }
    }

    private void sendCatalogEvent(String eventType, Long productId, Long userId) {
        try {
            Map<String, Object> message = Map.of(
                "eventType", eventType,
                "productId", productId,
                "userId", userId,
                "occurredAt", LocalDateTime.now().toString()
            );
            kafkaTemplate.send("catalog-events", String.valueOf(productId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CatalogEvent] Kafka 발행 실패 — eventType={}, productId={}",
                            eventType, productId, ex);
                    }
                });
        } catch (Exception e) {
            log.warn("[CatalogEvent] Kafka 전송 실패 — eventType={}, productId={}", eventType, productId, e);
        }
    }
}
