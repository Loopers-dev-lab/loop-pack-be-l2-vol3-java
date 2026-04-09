package com.loopers.application.eventlog;

import com.loopers.domain.event.UserActionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class UserActionEventKafkaPublisher {

    private static final String TOPIC = "user-action-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * 유저 행동 이벤트(조회 등) 트랜잭션 커밋 후 Kafka에 발행한다.
     * - AFTER_COMMIT: 트랜잭션 롤백 시 발행하지 않음
     * - @Async: API 응답 지연 방지
     * - key=targetId(productId): 같은 상품 이벤트가 동일 파티션으로 → 순서 보장
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserActionEvent event) {
        if (event.userId() == null) {
            return;
        }
        kafkaTemplate.send(TOPIC, String.valueOf(event.targetId()), event);
    }
}
