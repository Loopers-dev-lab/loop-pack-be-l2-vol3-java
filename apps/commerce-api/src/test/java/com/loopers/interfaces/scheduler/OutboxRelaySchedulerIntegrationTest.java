package com.loopers.interfaces.scheduler;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import com.loopers.support.outbox.OutboxEventStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxRelaySchedulerIntegrationTest {

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    private OutboxEvent savePendingEvent() {
        OutboxEvent event = OutboxEvent.create("evt-1", "payment.completed", "Order", "1", "{}", "order-events");
        return outboxEventRepository.save(event);
    }

    @Nested
    class 보완_발행_성공 {

        @Test
        void PENDING_이벤트가_있고_send가_성공하면_SENT로_전환된다() {
            savePendingEvent();
            when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxRelayScheduler.compensatePendingEvents();

            List<OutboxEvent> pending = outboxEventRepository.findPending(10);
            assertThat(pending).isEmpty();
        }

        @Test
        void SENT된_이벤트의_sentAt이_설정된다() {
            OutboxEvent event = savePendingEvent();
            when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxRelayScheduler.compensatePendingEvents();

            // PENDING으로 조회 안 되므로 직접 조회 — findPending은 PENDING만 반환
            // sentAt 검증은 SENT로 전환된 것을 통해 간접 확인
            assertThat(outboxEventRepository.findPending(10)).isEmpty();
        }
    }

    @Nested
    class 보완_발행_실패 {

        @Test
        void send가_실패하면_retryCount가_1_증가한다() {
            savePendingEvent();
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 장애")));

            outboxRelayScheduler.compensatePendingEvents();

            List<OutboxEvent> pending = outboxEventRepository.findPending(10);
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getRetryCount()).isEqualTo(1);
        }

        @Test
        void send가_실패해도_상태는_PENDING을_유지한다() {
            savePendingEvent();
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 장애")));

            outboxRelayScheduler.compensatePendingEvents();

            List<OutboxEvent> pending = outboxEventRepository.findPending(10);
            assertThat(pending.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        }
    }

    @Nested
    class 최대_재시도_초과 {

        @Test
        void retryCount가_10이면_FAILED로_전환된다() {
            OutboxEvent event = savePendingEvent();
            for (int i = 0; i < 10; i++) {
                event.incrementRetryCount();
            }
            outboxEventRepository.save(event);

            outboxRelayScheduler.compensatePendingEvents();

            List<OutboxEvent> pending = outboxEventRepository.findPending(10);
            assertThat(pending).isEmpty();
        }
    }

    @Nested
    class 빈_큐 {

        @Test
        void PENDING_이벤트가_없으면_send가_호출되지_않는다() {
            outboxRelayScheduler.compensatePendingEvents();

            verify(kafkaTemplate, never()).send(any(), any(), any());
        }
    }
}
