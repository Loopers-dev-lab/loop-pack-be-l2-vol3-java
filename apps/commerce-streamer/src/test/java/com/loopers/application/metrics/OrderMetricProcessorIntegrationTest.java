package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductOrderMetricRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderMetricProcessorIntegrationTest {

    @Autowired
    private OrderMetricProcessor processor;

    @Autowired
    private ProductOrderMetricRepository orderMetricRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String TOPIC = "order-events";
    private static final String GROUP = "test-group";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 주문_이벤트_처리 {

        @Test
        void 주문_이벤트를_처리하면_메트릭이_적재된다() {
            String eventId = UUID.randomUUID().toString();
            Instant occurredAt = Instant.parse("2026-04-10T05:03:00Z");
            List<OrderMetricProcessor.OrderItemMetric> items = List.of(
                    new OrderMetricProcessor.OrderItemMetric(1L, 2, 20000),
                    new OrderMetricProcessor.OrderItemMetric(2L, 1, 30000)
            );

            processor.process(eventId, "order.completed", TOPIC, GROUP, occurredAt, items);

            LocalDateTime from = LocalDateTime.of(2026, 4, 10, 5, 0);
            LocalDateTime to = LocalDateTime.of(2026, 4, 10, 5, 5);
            Map<Long, Long> result = orderMetricRepository.sumQuantityByBucketTimeRange(from, to, 100);

            assertThat(result).containsEntry(1L, 2L);
            assertThat(result).containsEntry(2L, 1L);
        }
    }

    @Nested
    class 복수_상품_처리 {

        @Test
        void 여러_상품이_포함된_주문을_모두_적재한다() {
            String eventId1 = UUID.randomUUID().toString();
            String eventId2 = UUID.randomUUID().toString();
            Instant occurredAt = Instant.parse("2026-04-10T05:03:00Z");

            processor.process(eventId1, "order.completed", TOPIC, GROUP, occurredAt,
                    List.of(new OrderMetricProcessor.OrderItemMetric(1L, 3, 30000)));
            processor.process(eventId2, "order.completed", TOPIC, GROUP, occurredAt,
                    List.of(new OrderMetricProcessor.OrderItemMetric(1L, 2, 20000)));

            LocalDateTime from = LocalDateTime.of(2026, 4, 10, 5, 0);
            LocalDateTime to = LocalDateTime.of(2026, 4, 10, 5, 5);
            Map<Long, Long> result = orderMetricRepository.sumQuantityByBucketTimeRange(from, to, 100);

            // 3 + 2 = 5
            assertThat(result.get(1L)).isEqualTo(5L);
        }
    }
}
