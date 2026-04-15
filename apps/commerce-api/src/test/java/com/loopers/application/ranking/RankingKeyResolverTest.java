package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingKeyResolverTest {

    private final RankingKeyResolver resolver = new RankingKeyResolver(
            Clock.system(ZoneId.of("Asia/Seoul")));

    @Nested
    class 실시간_키 {

        @Test
        void 그룹명이_포함된_실시간_키를_생성한다() {
            String key = resolver.resolve(RankingPeriod.REALTIME, LocalDate.of(2026, 4, 10), "control");

            assertThat(key).isEqualTo("ranking:realtime:control");
        }

        @Test
        void 실험_그룹_키를_생성한다() {
            String key = resolver.resolve(RankingPeriod.REALTIME, LocalDate.of(2026, 4, 10), "experiment");

            assertThat(key).isEqualTo("ranking:realtime:experiment");
        }
    }

    @Nested
    class 일간_키 {

        @Test
        void 날짜와_그룹명이_포함된_일간_키를_생성한다() {
            String key = resolver.resolve(RankingPeriod.DAILY, LocalDate.of(2026, 4, 10), "control");

            assertThat(key).isEqualTo("ranking:daily:20260410:control");
        }
    }

    @Nested
    class 롤링_7일_키 {

        @Test
        void 어제_기준_anchor_로_last7d_키를_생성한다() {
            // 조회 기준일 2026-04-15 → anchor_date = 2026-04-14 (오늘 제외)
            String key = resolver.resolve(RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), "control");

            assertThat(key).isEqualTo("ranking:last7d:20260414:control");
        }

        @Test
        void 실험_그룹별로_독립된_키를_생성한다() {
            String key = resolver.resolve(RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), "experiment_a");

            assertThat(key).isEqualTo("ranking:last7d:20260414:experiment_a");
        }

        @Test
        void 월_경계를_걸쳐도_음수_날짜없이_안전하게_계산된다() {
            // 조회 기준일 2026-01-01 → anchor_date = 2025-12-31
            String key = resolver.resolve(RankingPeriod.LAST_7D, LocalDate.of(2026, 1, 1), "control");

            assertThat(key).isEqualTo("ranking:last7d:20251231:control");
        }
    }

    @Nested
    class 롤링_30일_키 {

        @Test
        void 어제_기준_anchor_로_last30d_키를_생성한다() {
            String key = resolver.resolve(RankingPeriod.LAST_30D, LocalDate.of(2026, 4, 15), "control");

            assertThat(key).isEqualTo("ranking:last30d:20260414:control");
        }
    }

    @Nested
    class 기본_그룹 {

        @Test
        void 그룹_미지정시_control이_기본값이다() {
            String key = resolver.resolve(RankingPeriod.DAILY, LocalDate.of(2026, 4, 10));

            assertThat(key).isEqualTo("ranking:daily:20260410:control");
        }
    }

    @Nested
    class anchor_date_계산 {

        @Test
        void 오늘의_anchor_는_어제이다() {
            LocalDate anchor = resolver.anchorDateOf(LocalDate.of(2026, 4, 15));

            assertThat(anchor).isEqualTo(LocalDate.of(2026, 4, 14));
        }
    }
}
