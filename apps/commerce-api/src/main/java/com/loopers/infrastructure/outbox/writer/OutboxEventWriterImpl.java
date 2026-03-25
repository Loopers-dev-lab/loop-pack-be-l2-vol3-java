package com.loopers.infrastructure.outbox.writer;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.outbox.OutboxEventWriter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link OutboxEventWriter}의 구현체.
 *
 * <p>도메인 이벤트를 JSON으로 직렬화하고
 * {@link OutboxEventService}에 위임하여 저장한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventWriterImpl implements OutboxEventWriter {

    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @Override
    public void write(
            UUID eventId,
            Long aggregateId,
            String aggregateType,
            String eventType,
            Object event,
            String topic,
            String partitionKey
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventService.save(eventId, aggregateId, aggregateType, eventType, payload, topic, partitionKey);
            log.debug("[OUTBOX] aggregateType={}, eventType={}, aggregateId={}", aggregateType, eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("[OUTBOX] 직렬화 실패: aggregateType={}, eventType={}, aggregateId={}",
                    aggregateType, eventType, aggregateId, e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "Outbox 이벤트 직렬화 실패");
        }
    }
}
