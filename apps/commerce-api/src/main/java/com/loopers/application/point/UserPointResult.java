package com.loopers.application.point;

import com.loopers.domain.point.UserPoint;

import java.time.ZonedDateTime;

/**
 * 사용자 포인트 조회 결과.
 */
public record UserPointResult(
    Long id,
    Long userId,
    long balance,
    ZonedDateTime updatedAt
) {
    public static UserPointResult from(UserPoint point) {
        return new UserPointResult(
            point.getId(),
            point.getUserId(),
            point.getBalance(),
            point.getUpdatedAt()
        );
    }
}
