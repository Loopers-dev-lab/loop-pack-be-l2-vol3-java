package com.loopers.infrastructure.outbox.relay;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxEventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPollerTest {

    @Test
    @DisplayName("poll은 후보를 PROCESSING으로 전환한 이벤트만 반환한다")
    void poll_returnsOnlyMarkedProcessingEvents() {
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        OutboxEventPoller outboxEventPoller = new OutboxEventPoller(outboxEventRepository);

        OutboxEvent first = OutboxEvent.pending(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product_metrics",
                "product-1",
                "commerce.product.metrics.v1",
                "product-1",
                "{}",
                ZonedDateTime.now()
        );
        first = new OutboxEvent(1L, first.eventId(), first.eventType(), first.aggregateType(), first.aggregateId(), first.topic(), first.partitionKey(), first.payloadJson(), OutboxEventStatus.PENDING, first.attemptCount(), first.nextAttemptAt(), first.occurredAt(), first.publishedAt(), first.ackedAt());

        OutboxEvent second = OutboxEvent.pending(
                UUID.randomUUID(),
                "ORDER_CREATE",
                "product_metrics",
                "product-2",
                "commerce.product.metrics.v1",
                "product-2",
                "{}",
                ZonedDateTime.now()
        );
        second = new OutboxEvent(2L, second.eventId(), second.eventType(), second.aggregateType(), second.aggregateId(), second.topic(), second.partitionKey(), second.payloadJson(), OutboxEventStatus.PENDING, second.attemptCount(), second.nextAttemptAt(), second.occurredAt(), second.publishedAt(), second.ackedAt());

        when(outboxEventRepository.findPublishCandidates(any(), eq(10))).thenReturn(List.of(first, second));
        when(outboxEventRepository.markProcessing(eq(first.id()), any())).thenReturn(true);
        when(outboxEventRepository.markProcessing(eq(second.id()), any())).thenReturn(false);

        List<OutboxEvent> result = outboxEventPoller.poll(10);

        assertThat(result).containsExactly(first);
        verify(outboxEventRepository, times(2)).markProcessing(any(), any());
    }
}
