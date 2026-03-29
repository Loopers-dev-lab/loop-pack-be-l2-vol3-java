package com.loopers.domain.outbox;

import java.util.function.Consumer;

/**
 * Outbox 이벤트를 외부 메시지 브로커로 발행하는 Port.
 *
 * <p>도메인/인터페이스 레이어가 메시지 브로커 구현(Kafka 등)에 직접 의존하지 않도록
 * 발행 책임을 추상화한다.</p>
 */
public interface OutboxEventProducer {

    /**
     * Outbox 이벤트를 발행한다.
     *
     * @param outboxEvent 발행할 Outbox 이벤트
     * @param onSuccess   발행 성공 시 콜백
     * @param onFailure   발행 실패 시 콜백
     */
    void produceEvent(OutboxEvent outboxEvent, Runnable onSuccess, Consumer<Throwable> onFailure);
}
