package com.loopers.domain.payment;

/**
 * 결제 도메인 이벤트 발행 인터페이스.
 * 구현체는 infrastructure 레이어에서 발행 메커니즘(Spring Event, Kafka 등)을 결정한다.
 */
public interface PaymentEventPublisher {

    void publish(PaymentRequestEvent event);
}
