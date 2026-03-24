package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.infrastructure.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayScheduler 단위 테스트")
class OutboxRelaySchedulerTest {

    @InjectMocks
    private OutboxRelayScheduler scheduler;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private OutboxModel pendingOutbox() {
        return OutboxModel.create("product", "product-1", "LikedEvent", "catalog-events", "{\"productId\":\"product-1\"}");
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<Object, Object>> successFuture() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private CompletableFuture<SendResult<Object, Object>> failedFuture() {
        return CompletableFuture.failedFuture(new RuntimeException("kafka broker unavailable"));
    }

    @Nested
    @DisplayName("compensate()")
    class Compensate {

        @Test
        @DisplayName("PENDING 레코드가 없으면 send()를 호출하지 않는다")
        void relay_noPending_noSend() {
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of());

            scheduler.compensate();

            verify(kafkaEventPublisher, never()).send(any());
        }

        @Test
        @DisplayName("Kafka 발행 성공 시 상태가 PUBLISHED로 변경된다")
        void relay_success_marksPublished() {
            OutboxModel outbox = pendingOutbox();
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of(outbox));
            given(kafkaEventPublisher.send(outbox)).willReturn(successFuture());

            scheduler.compensate();

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(outbox.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("Kafka 발행 실패 시 retry_count가 1 증가하고 상태는 PENDING을 유지한다")
        void relay_failure_incrementsRetryCount() {
            OutboxModel outbox = pendingOutbox();
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of(outbox));
            given(kafkaEventPublisher.send(outbox)).willReturn(failedFuture());

            scheduler.compensate();

            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }

        @Test
        @DisplayName("발행 실패가 5회 누적되면 상태가 FAILED로 변경된다")
        void relay_fiveFailures_marksFailed() {
            OutboxModel outbox = pendingOutbox();
            ReflectionTestUtils.setField(outbox, "retryCount", 4);
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of(outbox));
            given(kafkaEventPublisher.send(outbox)).willReturn(failedFuture());

            scheduler.compensate();

            assertThat(outbox.getRetryCount()).isEqualTo(5);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        }

        @Test
        @DisplayName("복수 레코드 중 1건 실패해도 나머지 레코드를 계속 처리한다")
        void relay_oneFailure_continuesProcessingRest() {
            OutboxModel first = pendingOutbox();
            OutboxModel second = pendingOutbox();
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of(first, second));
            given(kafkaEventPublisher.send(first)).willReturn(failedFuture());
            given(kafkaEventPublisher.send(second)).willReturn(successFuture());

            scheduler.compensate();

            assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(second.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }

        @Test
        @DisplayName("PENDING 레코드 100개를 모두 발행한다")
        void relay_100PendingRecords_allPublished() {
            List<OutboxModel> pending = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> OutboxModel.create("product", "product-" + i, "LikedEvent", "catalog-events", "{}"))
                    .toList();
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(pending);
            given(kafkaEventPublisher.send(any())).willReturn(successFuture());

            scheduler.compensate();

            assertThat(pending).allMatch(o -> o.getStatus() == OutboxStatus.PUBLISHED);
        }
    }
}
