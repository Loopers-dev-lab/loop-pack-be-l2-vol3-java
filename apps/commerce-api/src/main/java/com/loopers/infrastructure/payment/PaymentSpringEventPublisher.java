package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentEventPublisher;
import com.loopers.domain.payment.PaymentRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentSpringEventPublisher implements PaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(PaymentRequestEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
