package com.loopers.application.metrics;

import com.loopers.application.ranking.RankingProperties;
import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class ProductMetricsConsumerService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final RedisProductRankingRepository redisProductRankingRepository;
    private final RankingProperties rankingProperties;
    private final ProductMetricsAckPublisher productMetricsAckPublisher;

    public ProductMetricsConsumerService(
            EventHandledRepository eventHandledRepository,
            ProductMetricsDailyRepository productMetricsDailyRepository,
            ProductMetricsHourlyRepository productMetricsHourlyRepository,
            RedisProductRankingRepository redisProductRankingRepository,
            RankingProperties rankingProperties,
            ProductMetricsAckPublisher productMetricsAckPublisher
    ) {
        this.eventHandledRepository = eventHandledRepository;
        this.productMetricsDailyRepository = productMetricsDailyRepository;
        this.productMetricsHourlyRepository = productMetricsHourlyRepository;
        this.redisProductRankingRepository = redisProductRankingRepository;
        this.rankingProperties = rankingProperties;
        this.productMetricsAckPublisher = productMetricsAckPublisher;
    }

    @Transactional
    public void consume(String consumerGroup, ProductMetricsEventMessage message) {
        boolean inserted = eventHandledRepository.markHandledIfAbsent(consumerGroup, message.eventId());
        if (!inserted) {
            return;
        }
        productMetricsDailyRepository.upsert(message);
        productMetricsHourlyRepository.upsert(message);

        double scoreDelta = message.deltaView() * rankingProperties.weight().view()
                + message.deltaLike() * rankingProperties.weight().like()
                + message.deltaRevenue() * rankingProperties.weight().sales();
        if (scoreDelta != 0D && rankingProperties.sync().immediateIncrementEnabled()) {
            LocalDate metricDate = message.updatedAt().atZone(KOREA_ZONE).toLocalDate();
            LocalDateTime metricHour = message.updatedAt().atZone(KOREA_ZONE).toLocalDateTime()
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisProductRankingRepository.incrementDailyRanking(metricDate, message.productId(), scoreDelta);
                        redisProductRankingRepository.incrementHourlyRanking(metricHour, message.productId(), scoreDelta);
                    }
                });
            } else {
                redisProductRankingRepository.incrementDailyRanking(metricDate, message.productId(), scoreDelta);
                redisProductRankingRepository.incrementHourlyRanking(metricHour, message.productId(), scoreDelta);
            }
        }

        productMetricsAckPublisher.publish(message.eventId(), consumerGroup);
    }
}
