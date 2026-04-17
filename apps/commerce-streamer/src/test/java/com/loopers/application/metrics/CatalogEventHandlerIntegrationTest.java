package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.ranking.RankingMetricsService;
import com.loopers.domain.ranking.RankingMetricsSummary;
import com.loopers.interfaces.consumer.OutboxMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CatalogEventHandlerIntegrationTest {

    private static final Long PRODUCT_ID = 100L;
    private static final Long USER_ID = 1L;

    @Autowired
    private CatalogEventHandler catalogEventHandler;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private RankingMetricsService rankingMetricsService;

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OutboxMessage likeCreatedMessage(Long eventId) {
        return new OutboxMessage(
                eventId, "PRODUCT", PRODUCT_ID, "LIKE_CREATED",
                "{\"productId\": " + PRODUCT_ID + ", \"userId\": " + USER_ID + "}");
    }

    private OutboxMessage likeCancelledMessage(Long eventId) {
        return new OutboxMessage(
                eventId, "PRODUCT", PRODUCT_ID, "LIKE_CANCELLED",
                "{\"productId\": " + PRODUCT_ID + ", \"userId\": " + USER_ID + "}");
    }

    private OutboxMessage productViewedMessage(Long eventId) {
        return new OutboxMessage(
                eventId, "PRODUCT", PRODUCT_ID, "PRODUCT_VIEWED",
                "{\"productId\": " + PRODUCT_ID + ", \"userId\": " + USER_ID + "}");
    }

    @DisplayName("LIKE_CREATED 이벤트 처리")
    @Nested
    class HandleLikeCreated {

        @DisplayName("좋아요 생성 이벤트를 처리하면 product_metrics의 like_count가 증가한다")
        @Test
        void likeCountIncremented() {
            // act
            catalogEventHandler.handle(likeCreatedMessage(1L));

            // assert
            ProductMetrics metrics = productMetricsRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("서로 다른 이벤트를 여러 번 처리하면 like_count가 누적된다")
        @Test
        void likeCountAccumulated() {
            // act
            catalogEventHandler.handle(likeCreatedMessage(1L));
            catalogEventHandler.handle(likeCreatedMessage(2L));
            catalogEventHandler.handle(likeCreatedMessage(3L));

            // assert
            ProductMetrics metrics = productMetricsRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(3);
        }
    }

    @DisplayName("LIKE_CANCELLED 이벤트 처리")
    @Nested
    class HandleLikeCancelled {

        @DisplayName("좋아요 취소 이벤트를 처리하면 product_metrics의 like_count가 감소한다")
        @Test
        void likeCountDecremented() {
            // arrange — 좋아요 2건 등록
            catalogEventHandler.handle(likeCreatedMessage(1L));
            catalogEventHandler.handle(likeCreatedMessage(2L));

            // act
            catalogEventHandler.handle(likeCancelledMessage(3L));

            // assert
            ProductMetrics metrics = productMetricsRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("like_count가 0이면 취소해도 음수가 되지 않는다")
        @Test
        void likeCountNotNegative() {
            // act
            catalogEventHandler.handle(likeCancelledMessage(1L));

            // assert
            ProductMetrics metrics = productMetricsRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(0);
        }
    }

    @DisplayName("PRODUCT_VIEWED 이벤트 처리")
    @Nested
    class HandleProductViewed {

        @DisplayName("조회 이벤트를 처리하면 ranking_metrics에 view_count가 반영된다")
        @Test
        void rankingMetricsViewCountReflected() {
            // act
            catalogEventHandler.handle(productViewedMessage(1L));

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID, LocalDate.now());
            assertThat(summary.totalViewCount()).isEqualTo(1);
        }

        @DisplayName("서로 다른 조회 이벤트가 여러 번 처리되면 view_count가 누적된다")
        @Test
        void viewCountAccumulated() {
            // act
            catalogEventHandler.handle(productViewedMessage(1L));
            catalogEventHandler.handle(productViewedMessage(2L));
            catalogEventHandler.handle(productViewedMessage(3L));

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID, LocalDate.now());
            assertThat(summary.totalViewCount()).isEqualTo(3);
        }
    }

    @DisplayName("LIKE_CREATED → ranking_metrics 반영")
    @Nested
    class LikeCreatedRankingMetrics {

        @DisplayName("좋아요 생성 이벤트를 처리하면 ranking_metrics에 like_count가 반영된다")
        @Test
        void rankingMetricsLikeCountReflected() {
            // act
            catalogEventHandler.handle(likeCreatedMessage(1L));

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID, LocalDate.now());
            assertThat(summary.totalLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("컨슈머 멱등성 보장 (event_handled)")
    @Nested
    class IdempotentHandling {

        @DisplayName("동일한 eventId의 이벤트를 두 번 처리해도 한 번만 반영된다")
        @Test
        void duplicateEventIgnored() {
            // arrange
            OutboxMessage message = likeCreatedMessage(1L);

            // act
            catalogEventHandler.handle(message);
            catalogEventHandler.handle(message); // 동일 이벤트 재처리

            // assert — like_count는 1 (한 번만 반영)
            ProductMetrics metrics = productMetricsRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("처리된 이벤트는 event_handled 테이블에 기록된다")
        @Test
        void eventHandledRecorded() {
            // act
            catalogEventHandler.handle(likeCreatedMessage(1L));

            // assert
            assertThat(eventHandledRepository.existsByEventId(1L)).isTrue();
        }

        @DisplayName("서로 다른 eventId는 각각 독립적으로 처리된다")
        @Test
        void differentEventsProcessedIndependently() {
            // act
            catalogEventHandler.handle(likeCreatedMessage(1L));
            catalogEventHandler.handle(likeCreatedMessage(2L));

            // assert
            assertThat(eventHandledRepository.existsByEventId(1L)).isTrue();
            assertThat(eventHandledRepository.existsByEventId(2L)).isTrue();

            ProductMetrics metrics = productMetricsRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(2);
        }
    }
}
