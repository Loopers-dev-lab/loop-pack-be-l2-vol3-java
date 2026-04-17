package com.loopers.application.queue;

public final class QueueConstants {

    public static final int BATCH_SIZE = 10;
    public static final int SCHEDULER_INTERVAL_SECONDS = 3;
    public static final long SCHEDULER_INTERVAL_MS = SCHEDULER_INTERVAL_SECONDS * 1000L;

    private QueueConstants() {}
}
