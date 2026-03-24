package com.loopers.domain.like.event;

import java.time.LocalDateTime;

public record LikeRemovedEvent(String eventId, Long productDbId, Long memberId, LocalDateTime removedAt) {
}
