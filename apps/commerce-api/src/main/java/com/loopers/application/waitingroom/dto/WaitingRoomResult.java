package com.loopers.application.waitingroom.dto;

import com.loopers.domain.waitingroom.WaitingRoomPosition;

public record WaitingRoomResult(
        String status,
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String token) {

    public static WaitingRoomResult from(WaitingRoomPosition position) {
        String status = position.isEntryReady() ? "ENTERED" : "WAITING";
        return new WaitingRoomResult(
                status,
                position.position(),
                position.totalWaiting(),
                position.estimatedWaitSeconds(),
                position.token());
    }
}
