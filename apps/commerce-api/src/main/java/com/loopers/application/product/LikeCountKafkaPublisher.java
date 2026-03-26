package com.loopers.application.product;

import com.loopers.application.like.event.LikeCancelledEvent;
import com.loopers.application.like.event.LikeRegisteredEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
public class LikeCountKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${loopers.kafka.topic.product-like-count:commerce.product.like-count.v1}")
    private String productLikeCountTopic;

    public LikeCountKafkaPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeRegisteredEvent event) {
        kafkaTemplate.send(
                productLikeCountTopic,
                event.productId().toString(),
                new LikeCountChangedMessage(UUID.randomUUID(), "LIKE_REGISTER", event.memberId(), event.productId(), 1, Instant.now())
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeCancelledEvent event) {
        kafkaTemplate.send(
                productLikeCountTopic,
                event.productId().toString(),
                new LikeCountChangedMessage(UUID.randomUUID(), "LIKE_CANCEL", event.memberId(), event.productId(), -1, Instant.now())
        );
    }
}
