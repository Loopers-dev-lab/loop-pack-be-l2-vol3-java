package com.loopers.application.like.event;

import java.util.UUID;

public record LikeRegisteredEvent(
        String memberId,
        UUID productId
) {
}
