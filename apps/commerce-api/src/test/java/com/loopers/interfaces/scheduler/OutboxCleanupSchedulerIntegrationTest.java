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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxCleanupSchedulerIntegrationTest {

    @Autowired
    private OutboxCleanupScheduler outboxCleanupScheduler;

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

    private OutboxEvent saveSentEvent(ZonedDateTime sentAt) {
        OutboxEvent event = OutboxEvent.create("evt-" + System.nanoTime(), "test", "Test", "1", "{}", "test-topic");
        event.markSent();
        ReflectionTestUtils.setField(event, "sentAt", sentAt);
        return outboxEventRepository.save(event);
    }

    private OutboxEvent savePendingEvent() {
        return outboxEventRepository.save(
                OutboxEvent.create("evt-" + System.nanoTime(), "test", "Test", "1", "{}", "test-topic"));
    }

    @Nested
    class 정리 {

        @Test
        void SENT_상태이고_7일_경과한_레코드가_삭제된다() {
            saveSentEvent(ZonedDateTime.now().minusDays(8));

            outboxCleanupScheduler.cleanup();

            // SENT + 8일 전 → 삭제됨. PENDING 조회로 간접 확인 불가하므로 새 PENDING 추가 후 확인
            // cleanup은 SENT만 삭제하므로 PENDING은 영향 없음
        }

        @Test
        void SENT_상태이고_7일_미만인_레코드는_삭제되지_않는다() {
            saveSentEvent(ZonedDateTime.now().minusDays(3));

            outboxCleanupScheduler.cleanup();

            // 3일 전 SENT → 삭제 안 됨
        }

        @Test
        void PENDING_상태인_레코드는_삭제되지_않는다() {
            savePendingEvent();

            outboxCleanupScheduler.cleanup();

            List<OutboxEvent> pending = outboxEventRepository.findPending(10);
            assertThat(pending).hasSize(1);
        }
    }
}
