package com.loopers.interfaces.consumer.payload;

import java.time.ZonedDateTime;

public record ProductViewEventPayload(
        Long productId,
        ZonedDateTime occurredAt
) {}
