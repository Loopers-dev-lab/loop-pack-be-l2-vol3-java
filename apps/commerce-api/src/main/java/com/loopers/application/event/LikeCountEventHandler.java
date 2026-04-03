package com.loopers.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductService;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.product.ProductCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountEventHandler {

    private final ProductService productService;
    private final ProductCacheManager productCacheManager;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleLiked(ProductLikedEvent event) {
        try {
            productService.incrementLikeCount(event.productId());
            productCacheManager.evictDetail(event.productId());
            publishToKafka("product.liked", event.productId(), event);
        } catch (Exception e) {
            log.error("좋아요 집계 실패: productId={}", event.productId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleUnliked(ProductUnlikedEvent event) {
        try {
            productService.decrementLikeCountIfPositive(event.productId());
            productCacheManager.evictDetail(event.productId());
            publishToKafka("product.unliked", event.productId(), event);
        } catch (Exception e) {
            log.error("좋아요 취소 집계 실패: productId={}", event.productId(), e);
        }
    }

    /**
     * 비핵심 지표(좋아요)는 Outbox 없이 직접 Kafka fire-and-forget.
     * 유실돼도 비즈니스 불변식이 깨지지 않는다. product_metrics 근사치로 충분.
     */
    private void publishToKafka(String eventType, Long productId, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, String.valueOf(productId), payload);
        } catch (Exception e) {
            log.warn("Kafka 직접 발행 실패 (fire-and-forget): eventType={}, productId={}", eventType, productId, e);
        }
    }
}
