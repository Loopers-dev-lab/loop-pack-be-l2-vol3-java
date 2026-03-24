package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.like.LikeEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeEventPublisherImpl implements LikeEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(LikeEvent.Created event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publish(LikeEvent.Deleted event) {
        eventPublisher.publishEvent(event);
    }
}
