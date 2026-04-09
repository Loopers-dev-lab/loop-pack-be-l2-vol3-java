package com.loopers.application.ranking;

import com.loopers.infrastructure.metrics.ProductMetricsDailyQueryRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyQueryRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRankingSyncApplicationServiceTest {

    @Mock
    private ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository;

    @Mock
    private ProductMetricsHourlyQueryRepository productMetricsHourlyQueryRepository;

    @Mock
    private RedisProductRankingRepository redisProductRankingRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2025-09-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 오늘_daily_metrics를_조회해_가중치_점수로_redis_일간_랭킹을_동기화한다() {
        ProductRankingSyncApplicationService productRankingSyncApplicationService = new ProductRankingSyncApplicationService(
                productMetricsDailyQueryRepository,
                productMetricsHourlyQueryRepository,
                redisProductRankingRepository,
                new RankingProperties(
                        new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
                        new RankingProperties.CarryOver(true, 0.1d, "0 50 23 * * *"),
                        new RankingProperties.Sync(true, 60000L)
                ),
                clock
        );
        LocalDate metricDate = LocalDate.of(2025, 9, 7);
        List<ProductMetricsSummary> metrics = List.of(
                new ProductMetricsSummary("product-1", 2L, 3L, 5000L, 10L),
                new ProductMetricsSummary("product-2", 1L, 0L, 0L, 5L)
        );
        when(productMetricsDailyQueryRepository.findByMetricDate(metricDate)).thenReturn(metrics);

        productRankingSyncApplicationService.syncCurrentDailyRanking();

        verify(redisProductRankingRepository).replaceDailyRanking(
                eq(metricDate),
                argThat(scores -> {
                    assertThat(scores).hasSize(2);
                    assertThat(scores.get("product-1")).isCloseTo(3501.4d, org.assertj.core.data.Offset.offset(0.000001d));
                    assertThat(scores.get("product-2")).isCloseTo(0.7d, org.assertj.core.data.Offset.offset(0.000001d));
                    return true;
                })
        );
    }

    @Test
    void 현재_hourly_metrics를_조회해_가중치_점수로_redis_시간별_랭킹을_동기화한다() {
        ProductRankingSyncApplicationService productRankingSyncApplicationService = new ProductRankingSyncApplicationService(
                productMetricsDailyQueryRepository,
                productMetricsHourlyQueryRepository,
                redisProductRankingRepository,
                new RankingProperties(
                        new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
                        new RankingProperties.CarryOver(true, 0.1d, "0 50 23 * * *"),
                        new RankingProperties.Sync(true, 60000L)
                ),
                clock
        );
        LocalDateTime metricHour = LocalDateTime.of(2025, 9, 7, 9, 0);
        List<ProductMetricsSummary> metrics = List.of(
                new ProductMetricsSummary("product-1", 1L, 1L, 1000L, 1L)
        );
        when(productMetricsHourlyQueryRepository.findByMetricHour(metricHour)).thenReturn(metrics);

        productRankingSyncApplicationService.syncCurrentHourlyRanking();

        verify(redisProductRankingRepository).replaceHourlyRanking(
                eq(metricHour),
                argThat(scores -> {
                    assertThat(scores.get("product-1")).isCloseTo(700.3d, org.assertj.core.data.Offset.offset(0.000001d));
                    return true;
                })
        );
    }

    @Test
    void 현재_일간_metrics가_비어있고_carry_over_키가_있으면_기존_랭킹판을_유지한다() {
        ProductRankingSyncApplicationService productRankingSyncApplicationService = new ProductRankingSyncApplicationService(
                productMetricsDailyQueryRepository,
                productMetricsHourlyQueryRepository,
                redisProductRankingRepository,
                new RankingProperties(
                        new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
                        new RankingProperties.CarryOver(true, 0.1d, "0 50 23 * * *"),
                        new RankingProperties.Sync(true, 60000L)
                ),
                clock
        );
        LocalDate metricDate = LocalDate.of(2025, 9, 7);
        when(productMetricsDailyQueryRepository.findByMetricDate(metricDate)).thenReturn(List.of());
        when(redisProductRankingRepository.hasDailyRanking(metricDate)).thenReturn(true);

        productRankingSyncApplicationService.syncCurrentDailyRanking();

        verify(redisProductRankingRepository, never()).replaceDailyRanking(eq(metricDate), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void 현재_시간별_metrics가_비어있고_carry_over_키가_있으면_기존_랭킹판을_유지한다() {
        ProductRankingSyncApplicationService productRankingSyncApplicationService = new ProductRankingSyncApplicationService(
                productMetricsDailyQueryRepository,
                productMetricsHourlyQueryRepository,
                redisProductRankingRepository,
                new RankingProperties(
                        new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
                        new RankingProperties.CarryOver(true, 0.1d, "0 50 23 * * *"),
                        new RankingProperties.Sync(true, 60000L)
                ),
                clock
        );
        LocalDateTime metricHour = LocalDateTime.of(2025, 9, 7, 9, 0);
        when(productMetricsHourlyQueryRepository.findByMetricHour(metricHour)).thenReturn(List.of());
        when(redisProductRankingRepository.hasHourlyRanking(metricHour)).thenReturn(true);

        productRankingSyncApplicationService.syncCurrentHourlyRanking();

        verify(redisProductRankingRepository, never()).replaceHourlyRanking(eq(metricHour), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void carry_over가_비활성화면_다음_랭킹판을_미리_생성하지_않는다() {
        ProductRankingSyncApplicationService productRankingSyncApplicationService = new ProductRankingSyncApplicationService(
                productMetricsDailyQueryRepository,
                productMetricsHourlyQueryRepository,
                redisProductRankingRepository,
                new RankingProperties(
                        new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
                        new RankingProperties.CarryOver(false, 0.1d, "0 50 23 * * *"),
                        new RankingProperties.Sync(true, 60000L)
                ),
                clock
        );

        productRankingSyncApplicationService.prepareTomorrowDailyRanking();
        productRankingSyncApplicationService.prepareNextHourlyRanking();

        verify(redisProductRankingRepository, never()).carryOverDailyRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble());
        verify(redisProductRankingRepository, never()).carryOverHourlyRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble());
    }
}

