package com.loopers.domain.event;

import java.time.ZonedDateTime;

public record CatalogEventPayload(
    Long userId,
    Long productId,
    String action,
    ZonedDateTime occurredAt
) {
}
