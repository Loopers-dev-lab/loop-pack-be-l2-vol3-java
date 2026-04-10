package com.loopers.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxEventType;
import com.loopers.domain.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProductViewed(ProductViewedEvent event) {
        log.info("상품 조회 이벤트 - productId: {}, memberId: {}", event.productId(), event.memberId());
        String payload = toJson(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "PRODUCT_VIEWED",
                "productId", event.productId(),
                "memberId", event.memberId(),
                "version", System.currentTimeMillis(),
                "createdAt", LocalDateTime.now().toString()
        ));
        outboxEventRepository.save(OutboxEvent.create(
                OutboxEventType.PRODUCT_VIEWED,
                String.valueOf(event.productId()),
                payload
        ));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}
