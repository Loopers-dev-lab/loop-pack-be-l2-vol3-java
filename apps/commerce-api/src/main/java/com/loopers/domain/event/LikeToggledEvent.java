package com.loopers.domain.event;

import java.time.ZonedDateTime;

public record LikeToggledEvent(Long productId, Long userId, boolean liked, ZonedDateTime occurredAt) {
}
