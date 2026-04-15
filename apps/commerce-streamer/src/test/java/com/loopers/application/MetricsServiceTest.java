package com.loopers.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.eventtracker.AggregateEventTrackerRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MetricsService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @InjectMocks
    private MetricsService metricsService;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private AggregateEventTrackerRepository aggregateEventTrackerRepository;

    @Mock
    private RankingService rankingService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final ZonedDateTime EVENT_CREATED_AT = ZonedDateTime.parse("2026-03-27T10:00:00+09:00");

    @Test
    @DisplayName("PRODUCT_VIEWED 이벤트 수신 시 viewCount를 +1 하고 랭킹 점수를 반영한다")
    void incrementsViewCountAndRanking_whenProductViewed() {
        // Arrange
        doNothing().when(eventHandledRepository).save("0");

        // Act
        metricsService.processEvent("0", "PRODUCT_VIEWED", "100", EVENT_CREATED_AT);

        // Assert
        verify(productMetricsRepository).incrementViewCount(100L, 1);
        verify(rankingService).addScore("PRODUCT_VIEWED", 100L, EVENT_CREATED_AT, 1.0);
        verify(aggregateEventTrackerRepository).upsert("100", "PRODUCT_VIEWED", EVENT_CREATED_AT);
    }

    @Test
    @DisplayName("PRODUCT_LIKED 이벤트 수신 시 likeCount를 +1 하고 랭킹 점수를 반영한다")
    void incrementsLikeCount_whenProductLiked() {
        // Arrange
        doNothing().when(eventHandledRepository).save("1");

        // Act
        metricsService.processEvent("1", "PRODUCT_LIKED", "100", EVENT_CREATED_AT);

        // Assert
        verify(productMetricsRepository).incrementLikeCount(100L, 1);
        verify(rankingService).addScore("PRODUCT_LIKED", 100L, EVENT_CREATED_AT, 1.0);
        verify(eventHandledRepository).save("1");
        verify(aggregateEventTrackerRepository).upsert("100", "PRODUCT_LIKED", EVENT_CREATED_AT);
    }

    @Test
    @DisplayName("PRODUCT_UNLIKED 이벤트 수신 시 likeCount를 -1 하고 랭킹 점수를 감소시킨다")
    void decrementsLikeCount_whenProductUnliked() {
        // Arrange
        doNothing().when(eventHandledRepository).save("2");

        // Act
        metricsService.processEvent("2", "PRODUCT_UNLIKED", "100", EVENT_CREATED_AT);

        // Assert
        verify(productMetricsRepository).incrementLikeCount(100L, -1);
        verify(rankingService).addScore("PRODUCT_UNLIKED", 100L, EVENT_CREATED_AT, -1.0);
        verify(eventHandledRepository).save("2");
        verify(aggregateEventTrackerRepository).upsert("100", "PRODUCT_UNLIKED", EVENT_CREATED_AT);
    }

    @Test
    @DisplayName("이미 처리된 이벤트는 skip 한다")
    void skips_whenEventAlreadyHandled() {
        // Arrange
        doThrow(new DataIntegrityViolationException("Duplicate entry"))
            .when(eventHandledRepository).save("3");

        // Act
        metricsService.processEvent("3", "PRODUCT_LIKED", "100", EVENT_CREATED_AT);

        // Assert
        verify(productMetricsRepository, never()).incrementLikeCount(100L, 1);
        verify(aggregateEventTrackerRepository, never()).upsert("100", "PRODUCT_LIKED", EVENT_CREATED_AT);
    }

    @Test
    @DisplayName("incremental 이벤트는 순서와 무관하게 항상 처리된다")
    void processesIncrementalEvent_regardlessOfOrder() {
        // Arrange
        ZonedDateTime olderEvent = ZonedDateTime.parse("2026-03-27T09:00:00+09:00");
        doNothing().when(eventHandledRepository).save("4");

        // Act
        metricsService.processEvent("4", "PRODUCT_LIKED", "100", olderEvent);

        // Assert - incremental 이벤트는 createdAt과 무관하게 처리
        verify(productMetricsRepository).incrementLikeCount(100L, 1);
        verify(aggregateEventTrackerRepository).upsert("100", "PRODUCT_LIKED", olderEvent);
    }

    @Test
    @DisplayName("ORDER_COMPLETED 이벤트 수신 시 최신 이벤트이면 salesCount를 집계하고 랭킹 점수를 반영한다")
    void incrementsSalesCount_whenOrderCompletedAndNewerEvent() {
        // Arrange
        doNothing().when(eventHandledRepository).save("5");
        when(aggregateEventTrackerRepository.isNewerEvent("200", "ORDER_COMPLETED", EVENT_CREATED_AT))
            .thenReturn(true);
        String payload = """
            {"orderId":200,"memberId":1,"totalAmount":50000,"productIds":[10,20]}
            """;

        // Act
        metricsService.processEvent("5", "ORDER_COMPLETED", "200", EVENT_CREATED_AT, payload);

        // Assert
        verify(productMetricsRepository).incrementSalesCount(10L, 1, 25000);
        verify(productMetricsRepository).incrementSalesCount(20L, 1, 25000);
        verify(rankingService).addScore("ORDER_COMPLETED", 10L, EVENT_CREATED_AT, 25000.0);
        verify(rankingService).addScore("ORDER_COMPLETED", 20L, EVENT_CREATED_AT, 25000.0);
        verify(aggregateEventTrackerRepository).upsert("200", "ORDER_COMPLETED", EVENT_CREATED_AT);
    }

    @Test
    @DisplayName("ORDER_COMPLETED 이벤트 수신 시 stale 이벤트이면 skip 한다")
    void skipsOrderCompleted_whenStaleEvent() {
        // Arrange
        ZonedDateTime staleCreatedAt = ZonedDateTime.parse("2026-03-27T08:00:00+09:00");
        doNothing().when(eventHandledRepository).save("6");
        when(aggregateEventTrackerRepository.isNewerEvent("200", "ORDER_COMPLETED", staleCreatedAt))
            .thenReturn(false);
        String payload = """
            {"orderId":200,"memberId":1,"totalAmount":50000,"productIds":[10,20]}
            """;

        // Act
        metricsService.processEvent("6", "ORDER_COMPLETED", "200", staleCreatedAt, payload);

        // Assert - stale 이벤트이므로 집계하지 않음
        verify(productMetricsRepository, never()).incrementSalesCount(eq(10L), eq(1L), eq(25000L));
        verify(productMetricsRepository, never()).incrementSalesCount(eq(20L), eq(1L), eq(25000L));
        verify(aggregateEventTrackerRepository).upsert("200", "ORDER_COMPLETED", staleCreatedAt);
    }

    @Test
    @DisplayName("ORDER_COMPLETED 첫 이벤트는 항상 처리된다")
    void processesFirstOrderCompletedEvent() {
        // Arrange
        doNothing().when(eventHandledRepository).save("7");
        when(aggregateEventTrackerRepository.isNewerEvent("300", "ORDER_COMPLETED", EVENT_CREATED_AT))
            .thenReturn(true);
        String payload = """
            {"orderId":300,"memberId":1,"totalAmount":30000,"productIds":[10]}
            """;

        // Act
        metricsService.processEvent("7", "ORDER_COMPLETED", "300", EVENT_CREATED_AT, payload);

        // Assert
        verify(productMetricsRepository).incrementSalesCount(10L, 1, 30000);
    }
}
