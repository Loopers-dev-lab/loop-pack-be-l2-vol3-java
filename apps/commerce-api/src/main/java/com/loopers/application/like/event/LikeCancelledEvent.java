package com.loopers.application.like.event;

import java.util.UUID;

public record LikeCancelledEvent(
        String memberId,
        UUID productId
) {
}
