package com.loopers.application.metrics;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BucketTimeUtilsTest {

    @Nested
    class truncate5min {

        @Test
        void 정각이면_그대로_반환한다() {
            Instant input = Instant.parse("2026-04-10T00:05:00Z");
            Instant result = BucketTimeUtils.truncate5min(input);
            assertThat(result).isEqualTo(Instant.parse("2026-04-10T00:05:00Z"));
        }

        @Test
        void 중간_시각이면_5분_단위로_내림한다() {
            Instant input = Instant.parse("2026-04-10T00:07:33Z");
            Instant result = BucketTimeUtils.truncate5min(input);
            assertThat(result).isEqualTo(Instant.parse("2026-04-10T00:05:00Z"));
        }

        @Test
        void 자정_직전이면_오십오분으로_내림한다() {
            Instant input = Instant.parse("2026-04-10T23:59:59Z");
            Instant result = BucketTimeUtils.truncate5min(input);
            assertThat(result).isEqualTo(Instant.parse("2026-04-10T23:55:00Z"));
        }
    }

    @Nested
    class kstDateToUtcBoundary {

        @Test
        void KST_날짜를_UTC_경계로_변환한다() {
            // KST 2026-04-10 00:00 = UTC 2026-04-09 15:00
            LocalDate kstDate = LocalDate.of(2026, 4, 10);
            LocalDateTime result = BucketTimeUtils.kstDateToUtcBoundary(kstDate);
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 4, 9, 15, 0));
        }

        @Test
        void 연초_경계를_올바르게_변환한다() {
            // KST 2026-01-01 00:00 = UTC 2025-12-31 15:00
            LocalDate kstDate = LocalDate.of(2026, 1, 1);
            LocalDateTime result = BucketTimeUtils.kstDateToUtcBoundary(kstDate);
            assertThat(result).isEqualTo(LocalDateTime.of(2025, 12, 31, 15, 0));
        }

        @Test
        void 월말_경계를_올바르게_변환한다() {
            // KST 2026-05-01 00:00 = UTC 2026-04-30 15:00
            LocalDate kstDate = LocalDate.of(2026, 5, 1);
            LocalDateTime result = BucketTimeUtils.kstDateToUtcBoundary(kstDate);
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 4, 30, 15, 0));
        }

        @Test
        void 일간_범위가_이십사시간_UTC_구간을_정확히_커버한다() {
            LocalDate kstDate = LocalDate.of(2026, 4, 10);
            LocalDateTime from = BucketTimeUtils.kstDateToUtcBoundary(kstDate);
            LocalDateTime to = BucketTimeUtils.kstDateToUtcBoundary(kstDate.plusDays(1));

            // KST 04-10 00:00 ~ 04-11 00:00 = UTC 04-09 15:00 ~ 04-10 15:00
            assertThat(from).isEqualTo(LocalDateTime.of(2026, 4, 9, 15, 0));
            assertThat(to).isEqualTo(LocalDateTime.of(2026, 4, 10, 15, 0));
        }
    }
}
