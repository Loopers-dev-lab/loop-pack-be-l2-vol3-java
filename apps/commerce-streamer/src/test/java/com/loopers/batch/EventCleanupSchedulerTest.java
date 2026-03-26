package com.loopers.batch;

import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.idempotency.EventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventCleanupScheduler 테스트")
class EventCleanupSchedulerTest {

    @Mock
    EventHandledRepository eventHandledRepository;

    @Mock
    EventLogRepository eventLogRepository;

    @InjectMocks
    EventCleanupScheduler scheduler;

    @Nested
    @DisplayName("event_handled 정리")
    class CleanupEventHandledTests {

        @Test
        @DisplayName("BATCH_SIZE 단위로 반복 삭제한다")
        void cleanupEventHandled_ShouldDeleteInBatches() {
            when(eventHandledRepository.deleteOlderThan(any(LocalDateTime.class), anyInt()))
                    .thenReturn(10_000)   // 1차: BATCH_SIZE만큼
                    .thenReturn(3_000);   // 2차: 나머지

            scheduler.cleanupEventHandled();

            verify(eventHandledRepository, times(2))
                    .deleteOlderThan(any(LocalDateTime.class), anyInt());
        }

        @Test
        @DisplayName("삭제할 건이 없으면 1회만 호출된다")
        void cleanupEventHandled_WhenEmpty_ShouldCallOnce() {
            when(eventHandledRepository.deleteOlderThan(any(LocalDateTime.class), anyInt()))
                    .thenReturn(0);

            scheduler.cleanupEventHandled();

            verify(eventHandledRepository, times(1))
                    .deleteOlderThan(any(LocalDateTime.class), anyInt());
        }
    }

    @Nested
    @DisplayName("event_log 정리")
    class CleanupEventLogTests {

        @Test
        @DisplayName("BATCH_SIZE 단위로 반복 삭제한다")
        void cleanupEventLog_ShouldDeleteInBatches() {
            when(eventLogRepository.deleteOlderThan(any(LocalDateTime.class), anyInt()))
                    .thenReturn(10_000)
                    .thenReturn(0);

            scheduler.cleanupEventLog();

            verify(eventLogRepository, times(2))
                    .deleteOlderThan(any(LocalDateTime.class), anyInt());
        }
    }
}
