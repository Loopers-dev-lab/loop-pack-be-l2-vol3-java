package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeCancelledEvent;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.like.LikeEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeSpringEventPublisher implements LikeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(LikeCreatedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(LikeCancelledEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
