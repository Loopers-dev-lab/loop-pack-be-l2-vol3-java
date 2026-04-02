package com.loopers.application.event;

import com.loopers.domain.useraction.ActionType;

import java.time.ZonedDateTime;

/**
 * 유저 행동 이벤트.
 * 사용자의 행동(조회, 클릭, 좋아요, 주문 등)을 기록하기 위해 발행됨.
 */
public record UserActionEvent(
    Long userId,
    ActionType actionType,
    Long targetId,
    ZonedDateTime occurredAt
) {
    public static UserActionEvent of(Long userId, ActionType actionType, Long targetId) {
        return new UserActionEvent(userId, actionType, targetId, ZonedDateTime.now());
    }
}
