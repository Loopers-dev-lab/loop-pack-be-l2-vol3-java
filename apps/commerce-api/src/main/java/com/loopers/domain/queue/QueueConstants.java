package com.loopers.domain.queue;

public final class QueueConstants {

    private QueueConstants() {}

    public static final String QUEUE_KEY = "queue:waiting";
    public static final String TOKEN_KEY_PREFIX = "token:";
    public static final String PRESENCE_KEY_PREFIX = "presence:";
    public static final long PRESENCE_TTL_SECONDS = 90L;
    public static final int BATCH_SIZE = 80;
    public static final long SCHEDULER_INTERVAL_SECONDS = 5;
}