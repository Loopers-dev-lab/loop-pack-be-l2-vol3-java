package com.loopers.domain.like;

public interface LikeEventPublisher {

    void publish(LikeEvent.Created event);

    void publish(LikeEvent.Deleted event);
}
