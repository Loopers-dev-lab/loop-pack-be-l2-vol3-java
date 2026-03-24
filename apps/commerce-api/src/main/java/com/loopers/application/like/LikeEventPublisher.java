package com.loopers.application.like;

import com.loopers.domain.like.event.LikeRemovedEvent;
import com.loopers.domain.like.event.LikedEvent;

public interface LikeEventPublisher {
    void publish(LikedEvent event);
    void publish(LikeRemovedEvent event);
}
