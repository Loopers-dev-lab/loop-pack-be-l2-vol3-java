package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScoreEncoderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @DisplayName("동일 base points 일 때, ")
    @Nested
    class TieBreakOrdering {

        @DisplayName("더 늦게 갱신된 ledger의 composite score가 더 크다.")
        @Test
        void laterUpdateRanksHigher() {
            String bucketKey = "20260406";
            Instant bucketStart = LocalDate.parse("2026-04-06").atStartOfDay(KST).toInstant();

            double earlier = RankingScoreEncoder.encode(
                RankingScoreLedger.BucketType.DAY, bucketKey, 12.34,
                bucketStart.plusSeconds(100)
            );
            double later = RankingScoreEncoder.encode(
                RankingScoreLedger.BucketType.DAY, bucketKey, 12.34,
                bucketStart.plusSeconds(200)
            );

            assertThat(later).isGreaterThan(earlier);
        }

        @DisplayName("base points 차이가 크면 tie-break 비트는 영향을 주지 않는다.")
        @Test
        void basePointsDominate() {
            String bucketKey = "20260406";
            Instant bucketStart = LocalDate.parse("2026-04-06").atStartOfDay(KST).toInstant();

            double bigPointsEarly = RankingScoreEncoder.encode(
                RankingScoreLedger.BucketType.DAY, bucketKey, 100.0,
                bucketStart.plusSeconds(10)
            );
            double smallPointsLate = RankingScoreEncoder.encode(
                RankingScoreLedger.BucketType.DAY, bucketKey, 99.9,
                bucketStart.plusSeconds(86000)
            );

            assertThat(bigPointsEarly).isGreaterThan(smallPointsLate);
        }
    }

    @DisplayName("DAY/HOUR 버킷 시작시각 파싱 시, ")
    @Nested
    class BucketStart {

        @DisplayName("DAY 버킷 키는 yyyyMMdd 자정 KST로 파싱된다.")
        @Test
        void parsesDayBucket() {
            long sec = RankingScoreEncoder.bucketStartEpochSec(
                RankingScoreLedger.BucketType.DAY, "20260406"
            );
            assertThat(sec).isEqualTo(
                LocalDate.parse("2026-04-06").atStartOfDay(KST).toEpochSecond()
            );
        }

        @DisplayName("HOUR 버킷 키는 yyyyMMddHH 정시 KST로 파싱된다.")
        @Test
        void parsesHourBucket() {
            long sec = RankingScoreEncoder.bucketStartEpochSec(
                RankingScoreLedger.BucketType.HOUR, "2026040613"
            );
            assertThat(sec).isEqualTo(
                LocalDateTime.parse("2026-04-06T13:00:00").atZone(KST).toEpochSecond()
            );
        }
    }
}
