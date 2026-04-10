package com.loopers.interfaces.waitingroom.dto;

import com.loopers.application.waitingroom.dto.WaitingRoomResult;

public class WaitingRoomResponse {

    public record PositionResponse(
            String status,
            long position,
            long totalWaiting,
            long estimatedWaitSeconds,
            String token) {

        public static PositionResponse from(WaitingRoomResult result) {
            return new PositionResponse(
                    result.status(),
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.token());
        }
    }
}
