package com.loopers.batch.job.ranking.param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * anchor_date 를 기준으로 계산된 LAST_7D / LAST_30D 롤링 윈도우 경계.
 *
 * - 오늘은 항상 제외된다 (anchor = 어제).
 * - 경계는 exclusive: bucket_time >= start AND bucket_time < end.
 * - last7dEnd 와 last30dEnd 는 동일 (= anchor + 1일 00:00). 둘 다 "오늘 0시" 를 상한으로 둠.
 */
public record RollingWindow(
        LocalDate anchorDate,
        String anchorDateKey,      // yyyyMMdd
        LocalDateTime last7dStart,
        LocalDateTime last7dEnd,
        LocalDateTime last30dStart,
        LocalDateTime last30dEnd
) {
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static RollingWindow of(LocalDate anchorDate) {
        LocalDateTime last7dStart  = anchorDate.minusDays(6).atStartOfDay();
        LocalDateTime last7dEnd    = anchorDate.plusDays(1).atStartOfDay();
        LocalDateTime last30dStart = anchorDate.minusDays(29).atStartOfDay();
        LocalDateTime last30dEnd   = anchorDate.plusDays(1).atStartOfDay();

        return new RollingWindow(
                anchorDate,
                anchorDate.format(KEY_FORMAT),
                last7dStart,
                last7dEnd,
                last30dStart,
                last30dEnd
        );
    }
}
