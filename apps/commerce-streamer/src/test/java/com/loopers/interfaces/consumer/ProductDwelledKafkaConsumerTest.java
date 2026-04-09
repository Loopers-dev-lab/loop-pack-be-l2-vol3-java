package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.collector.CouponIssueConsumeService;
import com.loopers.application.collector.EventDedupService;
import com.loopers.application.collector.ProductMetricsAggregationService;
import com.loopers.application.collector.RealtimeRankingAggregationService;
import com.loopers.kafka.message.KafkaEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductDwelledKafkaConsumerTest {

    private CommerceEventKafkaConsumer consumer;
    private RealtimeRankingAggregationService realtimeRankingAggregationService;
    private EventDedupService eventDedupService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        eventDedupService = mock(EventDedupService.class);
        realtimeRankingAggregationService = mock(RealtimeRankingAggregationService.class);
        ProductMetricsAggregationService productMetricsAggregationService = mock(ProductMetricsAggregationService.class);
        CouponIssueConsumeService couponIssueConsumeService = mock(CouponIssueConsumeService.class);

        consumer = new CommerceEventKafkaConsumer(
            objectMapper,
            eventDedupService,
            productMetricsAggregationService,
            realtimeRankingAggregationService,
            couponIssueConsumeService
        );
        ReflectionTestUtils.setField(consumer, "metricsConsumerGroup", "commerce-metrics-consumer");
    }

    @DisplayName("PRODUCT_DWELLED 이벤트를 소비하면 applyDwell이 호출된다")
    @Test
    void consumesProductDwelledEvent_andCallsApplyDwell() throws Exception {
        // arrange
        when(eventDedupService.markIfNotHandled(any(), any())).thenReturn(true);

        ZonedDateTime occurredAt = ZonedDateTime.now();
        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
            UUID.randomUUID().toString(),
            "PRODUCT_DWELLED",
            "PRODUCT",
            "1",
            "1",
            1,
            occurredAt,
            Map.of(
                "productId", 1L,
                "userId", 100L,
                "dwellTimeSeconds", 60
            )
        );

        String payload = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
            "catalog-events", 0, 0L, "1", payload
        );

        // act
        consumer.processMetricsRecord(record);

        // assert
        verify(realtimeRankingAggregationService).applyDwell(eq(1L), eq(100L), eq(60), any(ZonedDateTime.class));
    }

    @DisplayName("중복 이벤트는 무시된다")
    @Test
    void ignoresDuplicateEvent() throws Exception {
        // arrange
        when(eventDedupService.markIfNotHandled(any(), any())).thenReturn(false);

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
            UUID.randomUUID().toString(),
            "PRODUCT_DWELLED",
            "PRODUCT",
            "1",
            "1",
            1,
            ZonedDateTime.now(),
            Map.of(
                "productId", 1L,
                "userId", 100L,
                "dwellTimeSeconds", 60
            )
        );

        String payload = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
            "catalog-events", 0, 0L, "1", payload
        );

        // act
        consumer.processMetricsRecord(record);

        // assert
        verify(realtimeRankingAggregationService, never()).applyDwell(anyLong(), anyLong(), anyInt(), any());
    }
}
