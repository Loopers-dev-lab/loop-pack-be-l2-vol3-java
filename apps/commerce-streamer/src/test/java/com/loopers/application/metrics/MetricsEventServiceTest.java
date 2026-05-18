package com.loopers.application.metrics;

import com.loopers.application.ranking.RankingScoreService;
import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetricsEventServiceTest {

    private EventHandledRepository eventHandledRepository;
    private ProductMetricsRepository productMetricsRepository;
    private RankingScoreService rankingScoreService;
    private MetricsEventService metricsEventService;

    private static final Instant EVENT_TIME = Instant.parse("2026-03-26T10:00:00Z");

    @BeforeEach
    void setUp() {
        eventHandledRepository = mock(EventHandledRepository.class);
        productMetricsRepository = mock(ProductMetricsRepository.class);
        rankingScoreService = mock(RankingScoreService.class);
        metricsEventService = new MetricsEventService(eventHandledRepository, productMetricsRepository, rankingScoreService);
    }

    @DisplayName("PRODUCT_VIEWED 이벤트를 처리하면 viewCount를 증가시키고 랭킹 점수를 갱신한다")
    @Test
    void processViewedEvent() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

        metricsEventService.process("catalog.events.topic-v1", "event-1", "PRODUCT_VIEWED", 10L, 0, null, EVENT_TIME);

        verify(eventHandledRepository).save(any(EventHandled.class));
        verify(productMetricsRepository).upsertViewCount(eq(10L), any(LocalDate.class), eq(1));
        verify(rankingScoreService).updateScore("PRODUCT_VIEWED", 10L, 0);
    }

    @DisplayName("PRODUCT_LIKED 이벤트를 처리하면 likeCount를 1 증가시킨다")
    @Test
    void processLikedEvent() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

        metricsEventService.process("catalog.events.topic-v1", "event-2", "PRODUCT_LIKED", 10L, 0, null, EVENT_TIME);

        verify(eventHandledRepository).save(any(EventHandled.class));
        verify(productMetricsRepository).upsertLikeCount(eq(10L), any(LocalDate.class), eq(1));
    }

    @DisplayName("PRODUCT_UNLIKED 이벤트를 처리하면 likeCount를 1 감소시킨다")
    @Test
    void processUnlikedEvent() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

        metricsEventService.process("catalog.events.topic-v1", "event-3", "PRODUCT_UNLIKED", 10L, 0, null, EVENT_TIME);

        verify(eventHandledRepository).save(any(EventHandled.class));
        verify(productMetricsRepository).upsertLikeCount(eq(10L), any(LocalDate.class), eq(-1));
    }

    @DisplayName("ORDER_PLACED 이벤트를 처리하면 salesCount를 quantity만큼 증가시키고 랭킹 점수를 갱신한다")
    @Test
    void processOrderPlacedEvent() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

        metricsEventService.process("catalog.events.topic-v1", "event-4", "ORDER_PLACED", 10L, 3, null, EVENT_TIME);

        verify(eventHandledRepository).save(any(EventHandled.class));
        verify(productMetricsRepository).upsertSalesCount(eq(10L), any(LocalDate.class), eq(3));
        verify(rankingScoreService).updateScore("ORDER_PLACED", 10L, 3);
    }

    @DisplayName("ORDER_CANCELLED 이벤트를 처리하면 salesCount를 quantity만큼 감소시킨다")
    @Test
    void processOrderCancelledEvent() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

        metricsEventService.process("catalog.events.topic-v1", "event-5", "ORDER_CANCELLED", 10L, 2, null, EVENT_TIME);

        verify(eventHandledRepository).save(any(EventHandled.class));
        verify(productMetricsRepository).upsertSalesCount(eq(10L), any(LocalDate.class), eq(-2));
    }

    @DisplayName("PRODUCT_PRICE_CHANGED 이벤트를 처리하면 occurredAt 기반으로 가격을 upsert한다")
    @Test
    void processPriceChangedEvent() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

        BigDecimal newPrice = BigDecimal.valueOf(15000);
        metricsEventService.process("catalog.events.topic-v1", "event-6", "PRODUCT_PRICE_CHANGED", 10L, 0, newPrice, EVENT_TIME);

        verify(eventHandledRepository).save(any(EventHandled.class));
        verify(productMetricsRepository).upsertPrice(eq(10L), any(LocalDate.class), eq(newPrice), eq(EVENT_TIME));
    }

    @DisplayName("이미 처리된 eventId는 중복 처리하지 않는다")
    @Test
    void processDuplicateEvent_skips() {
        when(eventHandledRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(true);

        metricsEventService.process("catalog.events.topic-v1", "event-1", "PRODUCT_VIEWED", 10L, 0, null, EVENT_TIME);

        verify(eventHandledRepository, never()).save(any());
        verify(productMetricsRepository, never()).upsertViewCount(anyLong(), any(LocalDate.class), anyInt());
        verify(rankingScoreService, never()).updateScore(anyString(), anyLong(), anyInt());
    }
}
