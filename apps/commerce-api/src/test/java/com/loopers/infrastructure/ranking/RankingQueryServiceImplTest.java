package com.loopers.infrastructure.ranking;

import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.FakeMonthlyRankingRepository;
import com.loopers.domain.ranking.FakeRankingRepository;
import com.loopers.domain.ranking.FakeWeeklyRankingRepository;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingQueryServiceImplTest {

    private FakeRankingRepository rankingRepository;
    private FakeWeeklyRankingRepository weeklyRankingRepository;
    private FakeMonthlyRankingRepository monthlyRankingRepository;
    private RankingQueryServiceImpl rankingQueryService;

    @BeforeEach
    void setUp() {
        rankingRepository = new FakeRankingRepository();
        weeklyRankingRepository = new FakeWeeklyRankingRepository();
        monthlyRankingRepository = new FakeMonthlyRankingRepository();
        rankingQueryService = new RankingQueryServiceImpl(
            rankingRepository, weeklyRankingRepository, monthlyRankingRepository);
    }

    @DisplayName("일별 랭킹을 getRanking(DAILY, ...) 로 조회할 때, ")
    @Nested
    class GetDailyRanking {

        @DisplayName("페이징된 결과를 반환한다.")
        @Test
        void returnsPaginatedResult() {
            String key = "ranking:day:20260404";
            for (long i = 1; i <= 5; i++) {
                rankingRepository.addScore(key, i, i * 10.0);
            }

            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.DAILY, LocalDate.of(2026, 4, 4), 0, 2);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).productId()).isEqualTo(5L);
            assertThat(result.items().get(0).rank()).isEqualTo(1);
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
        }

        @DisplayName("데이터가 없으면 빈 결과를 반환한다.")
        @Test
        void returnsEmpty_whenNoData() {
            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.DAILY, LocalDate.of(2026, 4, 4), 0, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    @DisplayName("개별 상품 순위 조회할 때, ")
    @Nested
    class GetProductDailyRank {

        @DisplayName("해당 상품의 1-based 순위를 반환한다.")
        @Test
        void returnsRank() {
            rankingRepository.addScore("ranking:day:20260404", 101L, 100.0);
            rankingRepository.addScore("ranking:day:20260404", 102L, 50.0);

            Optional<Long> rank = rankingQueryService.getProductDailyRank(101L, "20260404");

            assertThat(rank).isPresent().contains(1L);
        }

        @DisplayName("랭킹에 없으면 empty를 반환한다.")
        @Test
        void returnsEmpty_whenNotInRanking() {
            Optional<Long> rank = rankingQueryService.getProductDailyRank(999L, "20260404");

            assertThat(rank).isEmpty();
        }
    }

    @DisplayName("기간 랭킹 조회 (period) 할 때, ")
    @Nested
    class GetRanking {

        @DisplayName("period=DAILY 면 Redis 기반 일간 랭킹을 반환한다.")
        @Test
        void dailyDelegatesToRedisRepository() {
            rankingRepository.addScore("ranking:day:20260407", 1L, 30.0);
            rankingRepository.addScore("ranking:day:20260407", 2L, 20.0);

            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.DAILY, LocalDate.of(2026, 4, 7), 0, 10);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).productId()).isEqualTo(1L);
        }

        @DisplayName("period=WEEKLY 면 해당 일자가 속한 ISO 주차로 주간 MV를 조회한다.")
        @Test
        void weeklyMapsBaseDateToIsoWeek() {
            // 2026-04-07(화) 은 2026-W15 에 속함
            weeklyRankingRepository.addScore("2026-W15", 10L, 100.0);
            weeklyRankingRepository.addScore("2026-W15", 20L, 50.0);

            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.WEEKLY, LocalDate.of(2026, 4, 7), 0, 10);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).productId()).isEqualTo(10L);
            assertThat(result.items().get(0).rank()).isEqualTo(1L);
            assertThat(result.totalElements()).isEqualTo(2);
        }

        @DisplayName("period=MONTHLY 면 해당 일자의 연-월로 월간 MV를 조회한다.")
        @Test
        void monthlyMapsBaseDateToYearMonth() {
            monthlyRankingRepository.addScore("2026-04", 100L, 500.0);

            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.MONTHLY, LocalDate.of(2026, 4, 15), 0, 10);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(100L);
        }

        @DisplayName("범위 초과 page 는 빈 배열을 반환하고 totalElements 는 유지된다.")
        @Test
        void outOfRangePageReturnsEmptyWithTotal() {
            for (long i = 1; i <= 100; i++) {
                weeklyRankingRepository.addScore("2026-W15", i, (double) i);
            }

            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.WEEKLY, LocalDate.of(2026, 4, 7), 10, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(100);
        }

        @DisplayName("period=WEEKLY 조회 중 DB 예외가 발생하면 그대로 전파된다 (fail-loud).")
        @Test
        void weeklyPropagatesRepositoryException() {
            weeklyRankingRepository.failWith(new RuntimeException("DB connection lost"));

            assertThatThrownBy(() -> rankingQueryService.getRanking(
                RankingPeriod.WEEKLY, LocalDate.of(2026, 4, 7), 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection lost");
        }

        @DisplayName("period=MONTHLY 조회 중 DB 예외가 발생하면 그대로 전파된다 (fail-loud).")
        @Test
        void monthlyPropagatesRepositoryException() {
            monthlyRankingRepository.failWith(new RuntimeException("DB connection lost"));

            assertThatThrownBy(() -> rankingQueryService.getRanking(
                RankingPeriod.MONTHLY, LocalDate.of(2026, 4, 7), 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection lost");
        }

        @DisplayName("period=DAILY 는 Week 9 의 Redis graceful degradation 을 유지하여 예외를 흡수한다.")
        @Test
        void dailyKeepsGracefulDegradation() {
            rankingRepository.failWith(new RuntimeException("Redis down"));

            PageResult<ProductRanking> result = rankingQueryService.getRanking(
                RankingPeriod.DAILY, LocalDate.of(2026, 4, 7), 0, 10);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }
}
