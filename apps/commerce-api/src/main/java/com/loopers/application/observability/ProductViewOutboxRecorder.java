package com.loopers.application.observability;

import com.loopers.domain.outbox.DomainEventTypes;
import com.loopers.domain.outbox.DomainKafkaTopics;
import com.loopers.domain.outbox.TransactionalOutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * 상품 상세 조회는 읽기 전용 트랜잭션에서 처리되므로 Outbox 적재는 별도 TX(REQUIRES_NEW)로 분리한다.
 */
@Component
public class ProductViewOutboxRecorder {

    private final TransactionalOutboxWriter transactionalOutboxWriter;

    public ProductViewOutboxRecorder(TransactionalOutboxWriter transactionalOutboxWriter) {
        this.transactionalOutboxWriter = Objects.requireNonNull(transactionalOutboxWriter);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProductViewed(Long productId) {
        if (productId == null) {
            return;
        }
        transactionalOutboxWriter.record(
                DomainKafkaTopics.PRODUCT_EVENTS,
                String.valueOf(productId),
                DomainEventTypes.PRODUCT_VIEWED,
                Map.of("productId", productId));
    }
}
