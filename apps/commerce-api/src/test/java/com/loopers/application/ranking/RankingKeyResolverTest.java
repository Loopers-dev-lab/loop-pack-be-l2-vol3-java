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
    class 주간_키 {

        @Test
        void ISO_주차가_포함된_주간_키를_생성한다() {
            // 2026-04-10 금요일 → ISO week 15
            String key = resolver.resolve(RankingPeriod.WEEKLY, LocalDate.of(2026, 4, 10), "control");

            assertThat(key).isEqualTo("ranking:weekly:202615:control");
        }

        @Test
        void 연초_주차가_올바르게_계산된다() {
            // 2026-01-01 목요일 → ISO week 1
            String key = resolver.resolve(RankingPeriod.WEEKLY, LocalDate.of(2026, 1, 1), "control");

            assertThat(key).isEqualTo("ranking:weekly:202601:control");
        }
    }

    @Nested
    class 월간_키 {

        @Test
        void 연월이_포함된_월간_키를_생성한다() {
            String key = resolver.resolve(RankingPeriod.MONTHLY, LocalDate.of(2026, 4, 10), "control");

            assertThat(key).isEqualTo("ranking:monthly:202604:control");
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
}
