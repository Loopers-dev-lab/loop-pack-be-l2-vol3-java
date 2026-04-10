package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class RankingDateUtils {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private RankingDateUtils() {
    }

    /**
     * KST 기준 날짜를 bucket_time 컬럼(UTC LocalDateTime) 경계로 변환.
     *
     * 예: KST 2026-04-10 → UTC LocalDateTime 2026-04-09T15:00
     */
    public static LocalDateTime kstDateToUtcBoundary(LocalDate kstDate) {
        return kstDate.atStartOfDay(SERVICE_ZONE)
                .toInstant()
                .atOffset(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
