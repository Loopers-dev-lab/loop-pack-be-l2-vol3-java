package com.loopers.application.metrics;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class BucketTimeUtils {

    private static final long BUCKET_MINUTES = 5;
    private static final long BUCKET_SECONDS = BUCKET_MINUTES * 60;

    private BucketTimeUtils() {
    }

    public static Instant truncate5min(Instant instant) {
        long epochSecond = instant.getEpochSecond();
        long truncated = epochSecond - (epochSecond % BUCKET_SECONDS);
        return Instant.ofEpochSecond(truncated);
    }

    public static LocalDateTime toLocalDateTime(Instant bucket) {
        return LocalDateTime.ofInstant(bucket, ZoneOffset.UTC);
    }

    public static Instant parseBucketEpochMillis(String key) {
        String epochMillisStr = key.substring(key.lastIndexOf(':') + 1);
        return Instant.ofEpochMilli(Long.parseLong(epochMillisStr));
    }
}
