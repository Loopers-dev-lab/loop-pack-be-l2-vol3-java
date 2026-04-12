package com.loopers.application.metrics;

import com.loopers.application.ranking.RankingProperties;
import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMetricsConsumerServiceTest {

    private static final RankingProperties RANKING_PROPERTIES = new RankingProperties(
            new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
            new RankingProperties.CarryOver(true, 0.1d, "0 50 23 * * *"),
            new RankingProperties.Sync(true, 60000L, true)
    );

    @Test
    @DisplayName("중복 이벤트가 아니면 집계를 반영하고 현재 랭킹판에 점수를 즉시 누적한다")
    void consume_newEvent_updatesMetricsAndRanking() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsDailyRepository productMetricsDailyRepository = mock(ProductMetricsDailyRepository.class);
        ProductMetricsHourlyRepository productMetricsHourlyRepository = mock(ProductMetricsHourlyRepository.class);
        RedisProductRankingRepository redisProductRankingRepository = mock(RedisProductRankingRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(
                        eventHandledRepository,
                        productMetricsDailyRepository,
                        productMetricsHourlyRepository,
                        redisProductRankingRepository,
                        RANKING_PROPERTIES,
                        productMetricsAckPublisher
                );

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                0,
                1000,
                1,
                1,
                Instant.parse("2025-09-07T00:15:00Z")
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(true);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsDailyRepository, times(1)).upsert(message);
        verify(productMetricsHourlyRepository, times(1)).upsert(message);
        verify(redisProductRankingRepository, times(1)).incrementDailyRanking(LocalDate.of(2025, 9, 7), "product-1", 700.3d);
        verify(redisProductRankingRepository, times(1)).incrementHourlyRanking(LocalDateTime.of(2025, 9, 7, 9, 0), "product-1", 700.3d);
        verify(productMetricsAckPublisher, times(1)).publish(message.eventId(), "collector");
    }

    @Test
    @DisplayName("즉시 increment가 비활성화면 metrics만 적재하고 Redis 랭킹 누적은 하지 않는다")
    void consume_whenImmediateIncrementDisabled_skipsRankingIncrement() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsDailyRepository productMetricsDailyRepository = mock(ProductMetricsDailyRepository.class);
        ProductMetricsHourlyRepository productMetricsHourlyRepository = mock(ProductMetricsHourlyRepository.class);
        RedisProductRankingRepository redisProductRankingRepository = mock(RedisProductRankingRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(
                        eventHandledRepository,
                        productMetricsDailyRepository,
                        productMetricsHourlyRepository,
                        redisProductRankingRepository,
                        new RankingProperties(
                                new RankingProperties.Weight(0.1d, 0.2d, 0.7d),
                                new RankingProperties.CarryOver(true, 0.1d, "0 50 23 * * *"),
                                new RankingProperties.Sync(true, 60000L, false)
                        ),
                        productMetricsAckPublisher
                );

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "ORDER_COMPLETED",
                "product-1",
                0,
                1,
                1000,
                0,
                1,
                Instant.parse("2025-09-07T00:15:00Z")
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(true);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsDailyRepository, times(1)).upsert(message);
        verify(productMetricsHourlyRepository, times(1)).upsert(message);
        verify(redisProductRankingRepository, never()).incrementDailyRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble());
        verify(redisProductRankingRepository, never()).incrementHourlyRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble());
        verify(productMetricsAckPublisher, times(1)).publish(message.eventId(), "collector");
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 집계와 랭킹 누적을 건너뛴다")
    void consume_duplicateEvent_skipsMetricsAndRanking() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsDailyRepository productMetricsDailyRepository = mock(ProductMetricsDailyRepository.class);
        ProductMetricsHourlyRepository productMetricsHourlyRepository = mock(ProductMetricsHourlyRepository.class);
        RedisProductRankingRepository redisProductRankingRepository = mock(RedisProductRankingRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(
                        eventHandledRepository,
                        productMetricsDailyRepository,
                        productMetricsHourlyRepository,
                        redisProductRankingRepository,
                        RANKING_PROPERTIES,
                        productMetricsAckPublisher
                );

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                0,
                1000,
                1,
                1,
                Instant.parse("2025-09-07T00:15:00Z")
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(false);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsDailyRepository, never()).upsert(message);
        verify(productMetricsHourlyRepository, never()).upsert(message);
        verify(redisProductRankingRepository, never()).incrementDailyRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble());
        verify(redisProductRankingRepository, never()).incrementHourlyRanking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble());
        verify(productMetricsAckPublisher, never()).publish(message.eventId(), "collector");
    }
}

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class ProductMetricsConsumerServiceIntegrationTest {

    @Autowired
    private ProductMetricsConsumerService productMetricsConsumerService;

    @MockBean
    private ProductMetricsAckPublisher productMetricsAckPublisher;

    @MockBean
    private RedisProductRankingRepository redisProductRankingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        entityManager.createNativeQuery("TRUNCATE TABLE event_handled").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_metrics_hourly").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_metrics_daily").executeUpdate();
    }

    @Test
    @DisplayName("같은 eventId를 두 번 소비해도 metrics 버킷과 랭킹 점수는 한 번만 누적된다")
    void consume_sameEventTwice_updatesMetricsAndRankingOnlyOnce() {
        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                2,
                3000,
                1000,
                1,
                Instant.parse("2025-09-07T00:15:00Z")
        );

        productMetricsConsumerService.consume("collector", message);
        productMetricsConsumerService.consume("collector", message);

        Object[] dailyRow = (Object[]) entityManager.createNativeQuery(
                        """
                        SELECT like_count, sales_count, sales_amount, view_count
                        FROM product_metrics_daily
                        WHERE product_id = :productId
                        """
                )
                .setParameter("productId", message.productId())
                .getSingleResult();

        Object[] hourlyRow = (Object[]) entityManager.createNativeQuery(
                        """
                        SELECT like_count, sales_count, sales_amount, view_count
                        FROM product_metrics_hourly
                        WHERE product_id = :productId
                        """
                )
                .setParameter("productId", message.productId())
                .getSingleResult();

        Number handledCount = (Number) entityManager.createNativeQuery(
                        """
                        SELECT COUNT(*)
                        FROM event_handled
                        WHERE consumer_group = :consumerGroup
                        """
                )
                .setParameter("consumerGroup", "collector")
                .getSingleResult();

        assertThat(((Number) dailyRow[0]).longValue()).isEqualTo(message.deltaLike());
        assertThat(((Number) dailyRow[1]).longValue()).isEqualTo(message.deltaSales());
        assertThat(((Number) dailyRow[2]).longValue()).isEqualTo(message.deltaRevenue());
        assertThat(((Number) dailyRow[3]).longValue()).isEqualTo(message.deltaView());
        assertThat(((Number) hourlyRow[0]).longValue()).isEqualTo(message.deltaLike());
        assertThat(((Number) hourlyRow[1]).longValue()).isEqualTo(message.deltaSales());
        assertThat(((Number) hourlyRow[2]).longValue()).isEqualTo(message.deltaRevenue());
        assertThat(((Number) hourlyRow[3]).longValue()).isEqualTo(message.deltaView());
        assertThat(handledCount.longValue()).isEqualTo(1L);
        verify(redisProductRankingRepository, times(1)).incrementDailyRanking(LocalDate.of(2025, 9, 7), "product-1", 2200.2d);
        verify(redisProductRankingRepository, times(1)).incrementHourlyRanking(LocalDateTime.of(2025, 9, 7, 9, 0), "product-1", 2200.2d);
        verify(productMetricsAckPublisher, times(1)).publish(message.eventId(), "collector");
    }
}
