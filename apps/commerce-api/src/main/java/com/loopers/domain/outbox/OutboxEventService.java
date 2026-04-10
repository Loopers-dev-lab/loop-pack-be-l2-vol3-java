package com.loopers.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Outbox 이벤트를 저장한다.
     * Propagation.MANDATORY: 호출자의 트랜잭션이 반드시 존재해야 한다.
     * → Facade 트랜잭션 안에서만 호출 가능 (비즈니스 로직과 원자적 저장 보장).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventModel save(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        OutboxEventModel model = OutboxEventModel.create(
                aggregateType, aggregateId, eventType, topic, partitionKey, payload
        );
        return outboxEventRepository.save(model);
    }
}
