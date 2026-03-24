package com.loopers.application.like.event;

import java.time.ZonedDateTime;

public record LikeEvent(
    Long userId,
    Long productId,
    LikeAction action,
    ZonedDateTime occurredAt
) {
    public enum LikeAction {
        LIKED, UNLIKED
    }
}
