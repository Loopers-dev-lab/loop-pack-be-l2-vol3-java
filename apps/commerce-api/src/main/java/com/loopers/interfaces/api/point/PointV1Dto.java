package com.loopers.interfaces.api.point;

import com.loopers.application.point.UserPointResult;

import java.time.ZonedDateTime;

/**
 * 포인트 API DTO.
 */
public class PointV1Dto {

    private PointV1Dto() {}

    /**
     * 포인트 응답.
     */
    public record PointResponseDto(
        Long userId,
        long balance,
        ZonedDateTime updatedAt
    ) {
        public static PointResponseDto from(UserPointResult result) {
            return new PointResponseDto(
                result.userId(),
                result.balance(),
                result.updatedAt()
            );
        }
    }

    /**
     * 포인트 충전 요청.
     */
    public record ChargePointRequestDto(
        long amount
    ) {}
}
