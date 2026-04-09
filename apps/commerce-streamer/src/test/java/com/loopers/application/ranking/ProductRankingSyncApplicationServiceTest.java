package com.loopers.application.ranking;

import com.loopers.infrastructure.metrics.ProductMetricsDailyQueryRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRankingSyncApplicationServiceTest {

    @Mock
    private ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository;

    @Mock
    private RedisProductRankingRepository redisProductRankingRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2025-09-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 오늘_daily_metrics를_조회해_가중치_점수로_redis_랭킹을_동기화한다() {
        ProductRankingSyncApplicationService productRankingSyncApplicationService = new ProductRankingSyncApplicationService(
                productMetricsDailyQueryRepository,
                redisProductRankingRepository,
                clock
        );
        LocalDate metricDate = LocalDate.of(2025, 9, 7);
        List<ProductDailyMetrics> metrics = List.of(
                new ProductDailyMetrics("product-1", 2L, 3L, 10L),
                new ProductDailyMetrics("product-2", 1L, 0L, 5L)
        );
        when(productMetricsDailyQueryRepository.findByMetricDate(metricDate)).thenReturn(metrics);

        productRankingSyncApplicationService.syncTodayRanking();

        verify(redisProductRankingRepository).replaceDailyRanking(
                eq(metricDate),
                argThat(scores -> {
                    assertThat(scores).hasSize(2);
                    assertThat(scores.get("product-1")).isCloseTo(3.5d, org.assertj.core.data.Offset.offset(0.000001d));
                    assertThat(scores.get("product-2")).isCloseTo(0.7d, org.assertj.core.data.Offset.offset(0.000001d));
                    return true;
                })
        );
    }
}
