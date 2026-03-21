package com.loopers.domain.like.event;

import java.time.LocalDateTime;

public record LikedEvent(Long productDbId, Long memberId, LocalDateTime likedAt) {
}
