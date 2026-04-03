package com.loopers.application.behavior;

import com.loopers.application.behavior.event.BehaviorActionType;
import com.loopers.application.behavior.event.BehaviorLoggedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BehaviorEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public BehaviorEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(BehaviorActionType actionType, String memberId, String productId, String orderId) {
        publish(actionType, memberId, productId, orderId, 1L);
    }

    public void publish(BehaviorActionType actionType, String memberId, String productId, String orderId, long quantity) {
        Instant occurredAt = Instant.now();
        applicationEventPublisher.publishEvent(new BehaviorLoggedEvent(
                UUID.randomUUID(),
                actionType,
                sanitize(memberId),
                sanitize(productId),
                sanitize(orderId),
                Math.max(quantity, 0L),
                occurredAt.toEpochMilli(),
                occurredAt
        ));
    }

    public String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value;
    }
}
