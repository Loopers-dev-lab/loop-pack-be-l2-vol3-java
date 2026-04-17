package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductLikeMetricRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractionMetricProcessorIntegrationTest {

    @Autowired
    private InteractionMetricProcessor processor;

    @Autowired
    private ProductLikeMetricRepository likeMetricRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String TOPIC = "product-interaction-events";
    private static final String GROUP = "test-group";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_이벤트_처리 {

        @Test
        void 좋아요_이벤트를_처리하면_메트릭이_증가한다() {
            String eventId = UUID.randomUUID().toString();
            Instant occurredAt = Instant.parse("2026-04-10T05:03:00Z");

            processor.process(eventId, "product.liked", TOPIC, GROUP, 1L, occurredAt);

            LocalDateTime from = LocalDateTime.of(2026, 4, 10, 5, 0);
            LocalDateTime to = LocalDateTime.of(2026, 4, 10, 5, 5);
            Map<Long, Long> result = likeMetricRepository.sumByBucketTimeRange(from, to, 100);

            assertThat(result.get(1L)).isEqualTo(1);
        }

        @Test
        void 좋아요_취소_이벤트를_처리하면_메트릭이_감소한다() {
            String likeEventId = UUID.randomUUID().toString();
            String unlikeEventId = UUID.randomUUID().toString();
            Instant occurredAt = Instant.parse("2026-04-10T05:03:00Z");

            processor.process(likeEventId, "product.liked", TOPIC, GROUP, 1L, occurredAt);
            processor.process(unlikeEventId, "product.unliked", TOPIC, GROUP, 1L, occurredAt);

            LocalDateTime from = LocalDateTime.of(2026, 4, 10, 5, 0);
            LocalDateTime to = LocalDateTime.of(2026, 4, 10, 5, 5);
            Map<Long, Long> result = likeMetricRepository.sumByBucketTimeRange(from, to, 100);

            assertThat(result.get(1L)).isEqualTo(0);
        }
    }

    @Nested
    class 여러_상품_처리 {

        @Test
        void 서로_다른_상품의_좋아요를_각각_적재한다() {
            processor.process(UUID.randomUUID().toString(), "product.liked", TOPIC, GROUP, 1L,
                    Instant.parse("2026-04-10T05:03:00Z"));
            processor.process(UUID.randomUUID().toString(), "product.liked", TOPIC, GROUP, 2L,
                    Instant.parse("2026-04-10T05:08:00Z"));

            LocalDateTime from = LocalDateTime.of(2026, 4, 10, 5, 0);
            LocalDateTime to = LocalDateTime.of(2026, 4, 10, 5, 10);
            Map<Long, Long> result = likeMetricRepository.sumByBucketTimeRange(from, to, 100);

            assertThat(result).containsEntry(1L, 1L);
            assertThat(result).containsEntry(2L, 1L);
        }
    }
}
