package com.loopers.domain.like.event;

import java.time.LocalDateTime;

public record LikeRemovedEvent(Long productDbId, Long memberId, LocalDateTime removedAt) {
}
