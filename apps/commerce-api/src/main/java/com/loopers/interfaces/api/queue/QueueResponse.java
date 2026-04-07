package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueEntryResult;
import com.loopers.domain.queue.QueuePositionResult;
import com.loopers.domain.queue.QueueStatus;

public class QueueResponse {

    public record Entry(
            QueueStatus status,
            long position,
            long totalWaiting,
            int estimatedWaitSeconds
    ) {
        public static Entry from(QueueEntryResult result) {
            return new Entry(
                    result.status(),
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds()
            );
        }
    }

    public record Position(
            QueueStatus status,
            long position,
            long totalWaiting,
            int estimatedWaitSeconds,
            int nextPollAfterMs,
            long tokenRemainingSeconds
    ) {
        public static Position from(QueuePositionResult result) {
            return new Position(
                    result.status(),
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.nextPollAfterMs(),
                    result.tokenRemainingSeconds()
            );
        }
    }
}
