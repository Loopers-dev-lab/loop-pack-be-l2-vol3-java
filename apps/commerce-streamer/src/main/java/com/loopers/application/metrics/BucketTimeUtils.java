package com.loopers.application.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class BucketTimeUtils {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

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

    /**
     * KST 기준 날짜를 bucket_time 컬럼(UTC LocalDateTime) 경계로 변환.
     *
     * 예: KST 2026-04-10 → UTC LocalDateTime 2026-04-09T15:00
     *    (KST 2026-04-10 00:00 = UTC 2026-04-09 15:00)
     */
    public static LocalDateTime kstDateToUtcBoundary(LocalDate kstDate) {
        return kstDate.atStartOfDay(SERVICE_ZONE)
                .toInstant()
                .atOffset(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
