package com.loopers.application;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventHandledServiceIntegrationTest {

    @Autowired
    private EventHandledService eventHandledService;

    @Autowired
    private ProductMetricsService productMetricsService;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("멱등 처리를 할 때,")
    @Nested
    class IdempotentProcessing {

        @DisplayName("같은 eventId로 두 번 처리해도 결과는 한 번만 반영된다")
        @Test
        void duplicateEventIdProcessedOnlyOnce() {
            // given
            String eventId = UUID.randomUUID().toString();
            Long productId = 1L;

            // when - 1번째: 처리됨
            if (!eventHandledService.isAlreadyHandled(eventId)) {
                productMetricsService.incrementLikeCount(productId);
                eventHandledService.markHandled(eventId);
            }

            // when - 2번째: 중복이므로 스킵
            if (!eventHandledService.isAlreadyHandled(eventId)) {
                productMetricsService.incrementLikeCount(productId);
                eventHandledService.markHandled(eventId);
            }

            // then
            ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId)
                    .orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("서로 다른 eventId면 각각 처리된다")
        @Test
        void differentEventIdsProcessedSeparately() {
            // given
            String eventId1 = UUID.randomUUID().toString();
            String eventId2 = UUID.randomUUID().toString();
            Long productId = 1L;

            // when
            if (!eventHandledService.isAlreadyHandled(eventId1)) {
                productMetricsService.incrementLikeCount(productId);
                eventHandledService.markHandled(eventId1);
            }

            if (!eventHandledService.isAlreadyHandled(eventId2)) {
                productMetricsService.incrementLikeCount(productId);
                eventHandledService.markHandled(eventId2);
            }

            // then
            ProductMetricsModel metrics = productMetricsRepository.findByProductId(productId)
                    .orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(2);
        }
    }
}
