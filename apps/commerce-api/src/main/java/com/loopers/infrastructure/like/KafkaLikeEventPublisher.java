package com.loopers.infrastructure.like;

import com.loopers.application.like.LikeEventPublisher;
import com.loopers.application.like.LikeOutboxPayload;
import com.loopers.domain.like.event.LikeRemovedEvent;
import com.loopers.domain.like.event.LikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaLikeEventPublisher implements LikeEventPublisher {

    private static final String TOPIC = "catalog-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void publish(LikedEvent event) {
        LikeOutboxPayload payload = new LikeOutboxPayload(
                event.eventId(), "LikedEvent", 1,
                event.productDbId(), event.memberId(), 1, event.likedAt());
        kafkaTemplate.send(TOPIC, String.valueOf(event.productDbId()), payload);
    }

    @Override
    public void publish(LikeRemovedEvent event) {
        LikeOutboxPayload payload = new LikeOutboxPayload(
                event.eventId(), "LikeRemovedEvent", 1,
                event.productDbId(), event.memberId(), -1, event.removedAt());
        kafkaTemplate.send(TOPIC, String.valueOf(event.productDbId()), payload);
    }
}
