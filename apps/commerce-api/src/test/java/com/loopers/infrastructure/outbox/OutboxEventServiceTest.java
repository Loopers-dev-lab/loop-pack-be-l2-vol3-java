package com.loopers.infrastructure.outbox;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxEventServiceTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("Outbox에 이벤트를 저장하면 PENDING 상태로 생성된다")
    void save_creates_pending_event() {
        // given
        Map<String, Object> payload = Map.of("orderId", 1L, "userId", 1L, "totalAmount", 29900);

        // when
        OutboxEventEntity saved = outboxEventService.save(
                "ORDER", 1L, "OrderConfirmedEvent", payload,
                "order-events-v1", "1"
        );

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("ORDER");
        assertThat(saved.getAggregateId()).isEqualTo(1L);
        assertThat(saved.getEventType()).isEqualTo("OrderConfirmedEvent");
        assertThat(saved.getTopic()).isEqualTo("order-events-v1");
        assertThat(saved.getPartitionKey()).isEqualTo("1");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("PENDING 이벤트를 조회할 수 있다")
    void find_pending_events() {
        // given
        outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        outboxEventService.save("PRODUCT", 100L, "ProductLikedEvent",
                Map.of("productId", 100L), "catalog-events-v1", "100");

        // when
        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingEvents(50);

        // then
        assertThat(pendingEvents).hasSize(2);
        assertThat(pendingEvents).allMatch(e -> e.getStatus() == OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("PUBLISHED 상태로 변경하면 PENDING 조회에서 제외된다")
    void published_event_excluded_from_pending_query() {
        // given
        OutboxEventEntity event = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        event.markPublished();
        outboxEventJpaRepository.save(event);

        // when
        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingEvents(50);

        // then
        assertThat(pendingEvents).isEmpty();
    }

    @Test
    @DisplayName("FAILED 상태로 변경하면 retryCount가 증가한다")
    void failed_event_increments_retry_count() {
        // given
        OutboxEventEntity event = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");

        // when
        event.markFailed("Connection refused");
        outboxEventJpaRepository.save(event);

        // then
        OutboxEventEntity found = outboxEventJpaRepository.findById(event.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(found.getRetryCount()).isEqualTo(1);
        assertThat(found.getErrorMessage()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("재시도 대상 이벤트를 조회할 수 있다")
    void find_retryable_events() {
        // given
        OutboxEventEntity event = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        event.markFailed("timeout");
        outboxEventJpaRepository.save(event);

        // when
        List<OutboxEventEntity> retryable = outboxEventJpaRepository.findRetryableEvents(5, 50);

        // then
        assertThat(retryable).hasSize(1);
        assertThat(retryable.get(0).getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxRetry 초과한 이벤트는 재시도 대상에서 제외된다")
    void exceeded_max_retry_excluded() {
        // given
        OutboxEventEntity event = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        // 5회 실패
        for (int i = 0; i < 5; i++) {
            event.markFailed("fail " + i);
        }
        outboxEventJpaRepository.save(event);

        // when
        List<OutboxEventEntity> retryable = outboxEventJpaRepository.findRetryableEvents(5, 50);

        // then
        assertThat(retryable).isEmpty();
    }

    @Test
    @DisplayName("payload가 JSON으로 직렬화되어 저장된다")
    void payload_serialized_as_json() {
        // given
        record TestPayload(Long orderId, Long userId, int totalAmount) {}
        TestPayload payload = new TestPayload(1L, 1L, 29900);

        // when
        OutboxEventEntity saved = outboxEventService.save(
                "ORDER", 1L, "OrderConfirmedEvent", payload,
                "order-events-v1", "1"
        );

        // then
        assertThat(saved.getPayload()).contains("\"orderId\":1");
        assertThat(saved.getPayload()).contains("\"totalAmount\":29900");
    }

    @Test
    @DisplayName("markProcessing 호출 시 상태가 PROCESSING으로 변경된다")
    void mark_processing_changes_status() {
        // given
        OutboxEventEntity event = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        // when
        event.markProcessing();
        outboxEventJpaRepository.save(event);

        // then
        OutboxEventEntity found = outboxEventJpaRepository.findById(event.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
    }

    @Test
    @DisplayName("PROCESSING 상태 이벤트를 조회할 수 있다")
    void find_processing_events() {
        // given
        OutboxEventEntity event1 = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        OutboxEventEntity event2 = outboxEventService.save("PRODUCT", 100L, "ProductLikedEvent",
                Map.of("productId", 100L), "catalog-events-v1", "100");

        event1.markProcessing();
        outboxEventJpaRepository.save(event1);

        // when
        List<OutboxEventEntity> processingEvents = outboxEventJpaRepository.findProcessingEvents(50);

        // then
        assertThat(processingEvents).hasSize(1);
        assertThat(processingEvents.get(0).getId()).isEqualTo(event1.getId());
        assertThat(processingEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);
    }

    @Test
    @DisplayName("PROCESSING 상태는 PENDING 조회에서 제외된다")
    void processing_excluded_from_pending_query() {
        // given
        OutboxEventEntity event1 = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        OutboxEventEntity event2 = outboxEventService.save("PRODUCT", 100L, "ProductLikedEvent",
                Map.of("productId", 100L), "catalog-events-v1", "100");

        event1.markProcessing();
        outboxEventJpaRepository.save(event1);

        // when
        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingEvents(50);

        // then
        assertThat(pendingEvents).hasSize(1);
        assertThat(pendingEvents.get(0).getId()).isEqualTo(event2.getId());
    }

    @Test
    @DisplayName("findPendingEventsForUpdate는 PENDING 상태만 조회한다")
    void find_pending_events_for_update() {
        // given
        OutboxEventEntity pending = outboxEventService.save("ORDER", 1L, "OrderConfirmedEvent",
                Map.of("orderId", 1L), "order-events-v1", "1");
        OutboxEventEntity processing = outboxEventService.save("ORDER", 2L, "OrderConfirmedEvent",
                Map.of("orderId", 2L), "order-events-v1", "2");
        processing.markProcessing();
        outboxEventJpaRepository.save(processing);

        // when
        List<OutboxEventEntity> result = outboxEventJpaRepository.findPendingEventsForUpdate(50);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(pending.getId());
        assertThat(result.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
