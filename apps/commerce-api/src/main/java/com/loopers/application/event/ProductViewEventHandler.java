package com.loopers.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 상품 조회 이벤트 핸들러.
 *
 * <p>트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되며,
 * 상품 조회 이벤트를 로깅하고 Kafka로 전송한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewEventHandler {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 상품 조회 이벤트를 처리한다 (로깅 + Kafka 전송).
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("[ProductViewed] productId={}, userId={}", event.productId(), event.userId());

        try {
            Map<String, Object> message = Map.of(
                "eventType", "PRODUCT_VIEWED",
                "productId", event.productId(),
                "userId", event.userId(),
                "occurredAt", LocalDateTime.now().toString()
            );
            String jsonMessage = objectMapper.writeValueAsString(message);
            kafkaTemplate.send("catalog-events", String.valueOf(event.productId()), jsonMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CatalogEvent] Kafka 발행 실패 — PRODUCT_VIEWED, productId={}",
                            event.productId(), ex);
                    }
                });
        } catch (Exception e) {
            log.warn("[CatalogEvent] Kafka 전송 실패 — PRODUCT_VIEWED, productId={}", event.productId(), e);
        }
    }
}
