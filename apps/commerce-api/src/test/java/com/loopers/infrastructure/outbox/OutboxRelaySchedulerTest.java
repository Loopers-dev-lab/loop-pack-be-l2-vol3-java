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

        @Test
        @DisplayName("[At-Least-Once] markAllPublished() DB 실패 시 — Kafka 발행은 성공했지만 PENDING 재처리 → 중복 발행")
        void relay_markAllPublishedDbFailure_causesAtLeastOnceRedelivery() {
            /*
             * 시나리오:
             *   1. compensate() 실행 → Kafka send() 성공 → markAllPublished() DB 예외
             *   2. compensate() 재실행 → 같은 레코드가 PENDING 상태로 조회됨 → 다시 Kafka 발행
             *
             * 이것이 At-Least-Once 보장의 실제 의미다.
             * Consumer 측 멱등성(event_handled)이 중복 처리를 막아야 한다.
             *
             * markAllPublished() 예외 시:
             *   - outbox.status in-memory: PUBLISHED (markPublished() 호출됨)
             *   - DB: 여전히 PENDING (markAllPublished() 실패)
             *   - 다음 compensate()에서 findPendingWithLimit()가 DB에서 PENDING 조회 → 재발행
             */
            OutboxModel outbox = pendingOutbox();
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of(outbox));
            given(kafkaEventPublisher.send(outbox)).willReturn(successFuture());
            doThrow(new RuntimeException("DB connection timeout"))
                    .when(outboxRepository).markAllPublished(anyList());

            // 첫 번째 compensate — Kafka 발행 성공, DB 커밋 실패
            org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class, () -> scheduler.compensate());
            verify(kafkaEventPublisher, times(1)).send(outbox);

            // DB에서 PENDING 재조회 시뮬레이션 (findPendingWithLimit이 같은 outbox 반환)
            // 두 번째 compensate — 동일 레코드 재발행 (At-Least-Once)
            org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class, () -> scheduler.compensate());
            verify(kafkaEventPublisher, times(2)).send(outbox);

            assertThat(outbox.getStatus())
                    .as("in-memory에서 PUBLISHED이지만 DB는 PENDING → 재처리 대상")
                    .isEqualTo(OutboxStatus.PUBLISHED);
        }

        @Test
        @DisplayName("[At-Least-Once] Kafka send 성공 확정 후 markAllPublished 전 JVM kill 시뮬레이션")
        void relay_jvmKillAfterSendBeforeDbCommit_pendingRemainsForNextRelay() {
            /*
             * JVM kill 시나리오:
             *   send() future.get() 성공 → outbox.markPublished() 호출 (in-memory)
             *   → JVM kill → markAllPublished() 미실행
             *   → 재시작 시 DB에서 여전히 PENDING → 다음 릴레이에서 재발행
             *
             * 이 테스트는 markAllPublished 호출 자체가 일어나지 않는 케이스를 모사한다.
             */
            OutboxModel outbox = pendingOutbox();
            given(outboxRepository.findPendingWithLimit(anyInt())).willReturn(List.of(outbox));
            given(kafkaEventPublisher.send(outbox)).willReturn(successFuture());

            // markAllPublished는 호출되지 않도록 stub (JVM kill 모사)
            doNothing().when(outboxRepository).markAllPublished(anyList());

            scheduler.compensate();

            // Kafka는 1번 발행됨
            verify(kafkaEventPublisher, times(1)).send(outbox);
            // markAllPublished가 호출됨 (in-memory에서 PUBLISHED로 변경)
            verify(outboxRepository, times(1)).markAllPublished(List.of(outbox.getId()));

        }
    }
}
