package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionResult;

public class QueueDto {

    public record WaitingResponse(String status, long rank, long estimatedWaitSeconds, long nextPollAfter) {
        public static WaitingResponse from(QueuePositionResult.Waiting w) {
            return new WaitingResponse("WAITING", w.rank(), w.estimatedWaitSeconds(), w.nextPollAfter());
        }
    }

    public record EnteredResponse(String status, String token) {
        public static EnteredResponse from(QueuePositionResult.Entered e) {
            return new EnteredResponse("ENTERED", e.token());
        }
    }
}
