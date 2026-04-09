package com.loopers.application.ranking;

import com.loopers.infrastructure.metrics.ProductMetricsDailyQueryRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductRankingSyncApplicationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final double VIEW_WEIGHT = 0.1d;
    private static final double LIKE_WEIGHT = 0.2d;
    private static final double SALES_WEIGHT = 0.7d;

    private final ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository;
    private final RedisProductRankingRepository redisProductRankingRepository;
    private final Clock clock;

    public ProductRankingSyncApplicationService(
            ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository,
            RedisProductRankingRepository redisProductRankingRepository,
            Clock clock
    ) {
        this.productMetricsDailyQueryRepository = productMetricsDailyQueryRepository;
        this.redisProductRankingRepository = redisProductRankingRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void syncTodayRanking() {
        LocalDate metricDate = LocalDate.now(clock.withZone(KOREA_ZONE));
        List<ProductDailyMetrics> metrics = productMetricsDailyQueryRepository.findByMetricDate(metricDate);
        Map<String, Double> rankingScores = metrics.stream()
                .collect(Collectors.toMap(
                        ProductDailyMetrics::productId,
                        metric -> metric.viewCount() * VIEW_WEIGHT
                                + metric.likeCount() * LIKE_WEIGHT
                                + metric.salesCount() * SALES_WEIGHT
                ));
        redisProductRankingRepository.replaceDailyRanking(metricDate, rankingScores);
    }
}
