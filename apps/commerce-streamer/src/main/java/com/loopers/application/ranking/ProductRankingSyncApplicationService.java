package com.loopers.application.ranking;

import com.loopers.infrastructure.metrics.ProductMetricsDailyQueryRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyQueryRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductRankingSyncApplicationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository;
    private final ProductMetricsHourlyQueryRepository productMetricsHourlyQueryRepository;
    private final RedisProductRankingRepository redisProductRankingRepository;
    private final RankingProperties rankingProperties;
    private final Clock clock;

    public ProductRankingSyncApplicationService(
            ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository,
            ProductMetricsHourlyQueryRepository productMetricsHourlyQueryRepository,
            RedisProductRankingRepository redisProductRankingRepository,
            RankingProperties rankingProperties,
            Clock clock
    ) {
        this.productMetricsDailyQueryRepository = productMetricsDailyQueryRepository;
        this.productMetricsHourlyQueryRepository = productMetricsHourlyQueryRepository;
        this.redisProductRankingRepository = redisProductRankingRepository;
        this.rankingProperties = rankingProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void syncCurrentDailyRanking() {
        LocalDate metricDate = LocalDate.now(clock.withZone(KOREA_ZONE));
        List<ProductMetricsSummary> metrics = productMetricsDailyQueryRepository.findByMetricDate(metricDate);
        if (metrics.isEmpty() && redisProductRankingRepository.hasDailyRanking(metricDate)) {
            return;
        }
        redisProductRankingRepository.replaceDailyRanking(metricDate, calculateScores(metrics));
    }

    @Transactional(readOnly = true)
    public void syncCurrentHourlyRanking() {
        LocalDateTime metricHour = LocalDateTime.ofInstant(clock.instant(), KOREA_ZONE)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        List<ProductMetricsSummary> metrics = productMetricsHourlyQueryRepository.findByMetricHour(metricHour);
        if (metrics.isEmpty() && redisProductRankingRepository.hasHourlyRanking(metricHour)) {
            return;
        }
        redisProductRankingRepository.replaceHourlyRanking(metricHour, calculateScores(metrics));
    }

    public void prepareTomorrowDailyRanking() {
        if (!rankingProperties.carryOver().enabled()) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
        redisProductRankingRepository.carryOverDailyRanking(today, today.plusDays(1), rankingProperties.carryOver().ratio());
    }

    public void prepareNextHourlyRanking() {
        if (!rankingProperties.carryOver().enabled()) {
            return;
        }
        LocalDateTime currentHour = LocalDateTime.ofInstant(clock.instant(), KOREA_ZONE)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        redisProductRankingRepository.carryOverHourlyRanking(currentHour, currentHour.plusHours(1), rankingProperties.carryOver().ratio());
    }

    public Map<String, Double> calculateScores(List<ProductMetricsSummary> metrics) {
        return metrics.stream()
                .collect(Collectors.toMap(
                        ProductMetricsSummary::productId,
                        metric -> metric.viewCount() * rankingProperties.weight().view()
                                + metric.likeCount() * rankingProperties.weight().like()
                                + metric.salesAmount() * rankingProperties.weight().sales()
                ));
    }
}
