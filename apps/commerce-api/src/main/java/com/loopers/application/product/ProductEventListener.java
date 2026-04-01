package com.loopers.application.product;

import com.loopers.domain.common.event.ProductViewedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 상품 조회 이벤트 리스너 — Kafka fire-and-forget 직접 발행
 *
 * 상품 조회(ProductFacade.getProductDetail)는 TX가 없으므로
 * @TransactionalEventListener 대신 @EventListener를 사용한다.
 *
 * Outbox를 사용하지 않는 이유:
 *   - 조회에는 비즈니스 TX가 없음 → "같은 TX에 저장" 불가
 *   - 조회 수 유실은 서비스 정합성에 영향 없음 (At-Most-Once 충분)
 *   - 매 요청마다 쓰기 TX를 여는 것은 커넥션 풀 낭비
 *
 * Kafka fire-and-forget:
 *   - kafkaTemplate.send() 호출 후 결과 대기 없음
 *   - 발행 실패 시 유실 허용 (조회 수는 보정 배치로 보완 가능)
 */
@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ProductEventListener(KafkaTemplate<Object, Object> kafkaTemplate,
                                 ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Async
    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String partitionKey = String.valueOf(event.productId());

            ProducerRecord<Object, Object> record = new ProducerRecord<>(
                    "catalog-events-v1", null, partitionKey, payload);
            record.headers()
                    .add(new RecordHeader("X-Event-Type",
                            "ProductViewedEvent".getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Aggregate-Type",
                            "PRODUCT".getBytes(StandardCharsets.UTF_8)));

            // fire-and-forget — 결과 대기 없음, 유실 허용
            kafkaTemplate.send(record);

            log.debug("[ProductEventListener] 조회 이벤트 발행 — productId={}", event.productId());

        } catch (Exception e) {
            // 발행 실패해도 비즈니스에 영향 없음 — 로깅만
            log.warn("[ProductEventListener] 조회 이벤트 발행 실패 — productId={}, error={}",
                    event.productId(), e.getMessage());
        }
    }
}
