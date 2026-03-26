package com.loopers.domain.event;

public record UserActivityEvent(Long userId, String activityType, Long targetId, String targetType) {
}
