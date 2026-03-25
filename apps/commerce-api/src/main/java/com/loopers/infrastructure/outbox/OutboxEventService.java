package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 이벤트 저장 서비스
 *
 * 비즈니스 TX 안에서 호출된다.
 * @Transactional(REQUIRED)이므로 호출자의 TX에 참여한다.
 */
@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventJpaRepository outboxEventJpaRepository,
                               ObjectMapper objectMapper) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutboxEventEntity save(String aggregateType, Long aggregateId,
                                   String eventType, Object payload,
                                   String topic, String partitionKey) {
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 페이로드 직렬화 실패", e);
        }

        OutboxEventEntity entity = OutboxEventEntity.create(
                aggregateType, aggregateId, eventType, jsonPayload, topic, partitionKey);
        OutboxEventEntity saved = outboxEventJpaRepository.save(entity);

        log.info("[Outbox] 이벤트 저장 — id={}, topic={}, key={}, type={}",
                saved.getId(), topic, partitionKey, eventType);

        return saved;
    }
}
