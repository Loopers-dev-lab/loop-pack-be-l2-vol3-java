package com.loopers.domain.waitingroom;

public record WaitingRoomPosition(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String token) {

    public static WaitingRoomPosition of(long rank, long totalWaiting, long throughputPerSecond) {
        long estimatedWaitSeconds = throughputPerSecond > 0 ? rank / throughputPerSecond : 0;
        return new WaitingRoomPosition(rank + 1, totalWaiting, estimatedWaitSeconds, null);
    }

    public static WaitingRoomPosition ready(String token) {
        return new WaitingRoomPosition(0, 0, 0, token);
    }

    public boolean isEntryReady() {
        return token != null;
    }
}
