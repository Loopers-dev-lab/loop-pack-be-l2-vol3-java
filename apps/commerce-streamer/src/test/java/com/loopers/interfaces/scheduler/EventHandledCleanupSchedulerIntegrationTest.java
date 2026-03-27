package com.loopers.interfaces.scheduler;

import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import com.loopers.domain.log.EventLog;
import com.loopers.domain.log.EventLogRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventHandledCleanupSchedulerIntegrationTest {

    @Autowired
    private EventHandledCleanupScheduler scheduler;

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveEventHandled(String eventId, ZonedDateTime handledAt) {
        EventHandled handled = EventHandled.create(eventId, "test.event");
        ReflectionTestUtils.setField(handled, "handledAt", handledAt);
        eventHandledRepository.save(handled);
    }

    private void saveEventLog(String eventId, ZonedDateTime createdAt) {
        EventLog log = EventLog.processed(eventId, "test.event", "test-topic", "test-group", 10);
        ReflectionTestUtils.setField(log, "createdAt", createdAt);
        eventLogRepository.save(log);
    }

    @Nested
    class 정리 {

        @Test
        void 칠일_경과한_event_handled가_삭제된다() {
            saveEventHandled("old-evt", ZonedDateTime.now().minusDays(8));
            saveEventHandled("new-evt", ZonedDateTime.now().minusDays(1));

            scheduler.cleanup();

            assertThat(eventHandledRepository.existsByEventId("old-evt")).isFalse();
            assertThat(eventHandledRepository.existsByEventId("new-evt")).isTrue();
        }

        @Test
        void 칠일_미만인_레코드는_삭제되지_않는다() {
            saveEventHandled("recent-evt", ZonedDateTime.now().minusDays(3));

            scheduler.cleanup();

            assertThat(eventHandledRepository.existsByEventId("recent-evt")).isTrue();
        }
    }
}
