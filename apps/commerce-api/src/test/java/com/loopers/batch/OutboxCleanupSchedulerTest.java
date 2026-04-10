package com.loopers.batch;

import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxCleanupScheduler 테스트")
class OutboxCleanupSchedulerTest {

    @Mock
    OutboxEventRepository outboxEventRepository;

    @InjectMocks
    OutboxCleanupScheduler scheduler;

    @Test
    @DisplayName("PUBLISHED 이벤트를 BATCH_SIZE 단위로 반복 삭제한다")
    void cleanupOutbox_ShouldDeletePublishedInBatches() {
        // 첫 번째 호출: 10000건 삭제 (BATCH_SIZE만큼), 두 번째: 5000건 (마지막 배치)
        when(outboxEventRepository.deletePublishedOlderThan(any(LocalDateTime.class), anyInt()))
                .thenReturn(10_000)
                .thenReturn(5_000);
        when(outboxEventRepository.deleteDeadOlderThan(any(LocalDateTime.class), anyInt()))
                .thenReturn(0);

        scheduler.cleanupOutbox();

        verify(outboxEventRepository, times(2))
                .deletePublishedOlderThan(any(LocalDateTime.class), anyInt());
    }

    @Test
    @DisplayName("삭제할 건이 없으면 1회만 호출된다")
    void cleanupOutbox_WhenNoRecords_ShouldCallOnce() {
        when(outboxEventRepository.deletePublishedOlderThan(any(LocalDateTime.class), anyInt()))
                .thenReturn(0);
        when(outboxEventRepository.deleteDeadOlderThan(any(LocalDateTime.class), anyInt()))
                .thenReturn(0);

        scheduler.cleanupOutbox();

        verify(outboxEventRepository, times(1))
                .deletePublishedOlderThan(any(LocalDateTime.class), anyInt());
        verify(outboxEventRepository, times(1))
                .deleteDeadOlderThan(any(LocalDateTime.class), anyInt());
    }
}
