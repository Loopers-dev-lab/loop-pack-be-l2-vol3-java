package com.loopers.application.metrics;

import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMetricsConsumerServiceTest {

    @Test
    @DisplayName("중복 이벤트가 아니면 집계를 반영한다")
    void consume_newEvent_updatesMetrics() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
        ProductMetricsDailyRepository productMetricsDailyRepository = mock(ProductMetricsDailyRepository.class);
        ProductMetricsHourlyRepository productMetricsHourlyRepository = mock(ProductMetricsHourlyRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(
                        eventHandledRepository,
                        productMetricsRepository,
                        productMetricsDailyRepository,
                        productMetricsHourlyRepository,
                        productMetricsAckPublisher
                );

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                0,
                0,
                1000,
                Instant.now()
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(true);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsRepository, times(1)).upsert(message);
        verify(productMetricsDailyRepository, times(1)).upsert(message);
        verify(productMetricsHourlyRepository, times(1)).upsert(message);
        verify(productMetricsAckPublisher, times(1)).publish(message.eventId(), "collector");
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 집계를 건너뛴다")
    void consume_duplicateEvent_skipsMetrics() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
        ProductMetricsDailyRepository productMetricsDailyRepository = mock(ProductMetricsDailyRepository.class);
        ProductMetricsHourlyRepository productMetricsHourlyRepository = mock(ProductMetricsHourlyRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(
                        eventHandledRepository,
                        productMetricsRepository,
                        productMetricsDailyRepository,
                        productMetricsHourlyRepository,
                        productMetricsAckPublisher
                );

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                0,
                0,
                1000,
                Instant.now()
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(false);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsRepository, never()).upsert(message);
        verify(productMetricsDailyRepository, never()).upsert(message);
        verify(productMetricsHourlyRepository, never()).upsert(message);
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

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        entityManager.createNativeQuery("TRUNCATE TABLE event_handled").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_metrics_hourly").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_metrics_daily").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_metrics").executeUpdate();
    }

    @Test
    @DisplayName("같은 eventId를 두 번 소비해도 metrics 버킷은 한 번만 누적된다")
    void consume_sameEventTwice_updatesMetricsOnlyOnce() {
        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                2,
                3,
                1000,
                Instant.now()
        );

        productMetricsConsumerService.consume("collector", message);
        productMetricsConsumerService.consume("collector", message);

        Object[] totalRow = (Object[]) entityManager.createNativeQuery(
                        """
                        SELECT like_count, sales_count, view_count
                        FROM product_metrics
                        WHERE product_id = :productId
                        """
                )
                .setParameter("productId", message.productId())
                .getSingleResult();

        Object[] dailyRow = (Object[]) entityManager.createNativeQuery(
                        """
                        SELECT like_count, sales_count, view_count
                        FROM product_metrics_daily
                        WHERE product_id = :productId
                        """
                )
                .setParameter("productId", message.productId())
                .getSingleResult();

        Object[] hourlyRow = (Object[]) entityManager.createNativeQuery(
                        """
                        SELECT like_count, sales_count, view_count
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

        assertThat(((Number) totalRow[0]).longValue()).isEqualTo(message.deltaLike());
        assertThat(((Number) totalRow[1]).longValue()).isEqualTo(message.deltaSales());
        assertThat(((Number) totalRow[2]).longValue()).isEqualTo(message.deltaView());
        assertThat(((Number) dailyRow[0]).longValue()).isEqualTo(message.deltaLike());
        assertThat(((Number) dailyRow[1]).longValue()).isEqualTo(message.deltaSales());
        assertThat(((Number) dailyRow[2]).longValue()).isEqualTo(message.deltaView());
        assertThat(((Number) hourlyRow[0]).longValue()).isEqualTo(message.deltaLike());
        assertThat(((Number) hourlyRow[1]).longValue()).isEqualTo(message.deltaSales());
        assertThat(((Number) hourlyRow[2]).longValue()).isEqualTo(message.deltaView());
        assertThat(handledCount.longValue()).isEqualTo(1L);
        verify(productMetricsAckPublisher, times(1)).publish(message.eventId(), "collector");
    }
}
