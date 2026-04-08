package com.loopers.infrastructure.outbox;

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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxEventServiceIntegrationTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Nested
    class Outbox_저장 {

        @Test
        void saveAndPublish하면_Outbox_레코드가_PENDING으로_저장된다() {
            transactionTemplate.executeWithoutResult(status ->
                    outboxEventService.saveAndPublish("payment.completed", "Order", "1", "order-events", "{}")
            );

            // findPending은 createdAt < now-10s 조건이라 stale 윈도우를 기다려야 한다
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(outboxEventRepository.findPending(10)).hasSize(1));

            List<OutboxEvent> events = outboxEventRepository.findPending(10);
            assertThat(events.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        }

        @Test
        void 저장된_레코드의_eventType_aggregateType_topic이_일치한다() {
            transactionTemplate.executeWithoutResult(status ->
                    outboxEventService.saveAndPublish("payment.completed", "Order", "42", "order-events", "{}")
            );

            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(outboxEventRepository.findPending(10)).hasSize(1));

            OutboxEvent event = outboxEventRepository.findPending(10).get(0);
            assertAll(
                    () -> assertThat(event.getEventType()).isEqualTo("payment.completed"),
                    () -> assertThat(event.getAggregateType()).isEqualTo("Order"),
                    () -> assertThat(event.getAggregateId()).isEqualTo("42"),
                    () -> assertThat(event.getTopic()).isEqualTo("order-events")
            );
        }
    }

    @Nested
    class TX_전파 {

        @Test
        void TX_없이_saveAndPublish를_호출하면_IllegalTransactionStateException이_발생한다() {
            assertThatThrownBy(() ->
                    outboxEventService.saveAndPublish("test", "Test", "1", "test-topic", "{}"))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }
}
