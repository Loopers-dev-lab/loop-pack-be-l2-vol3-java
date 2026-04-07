package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntrySchedulerServiceTest {

    private static final String EVENT_ID = "default";
    private static final String LOCK_KEY = "queue:scheduler:lock";
    private static final String HEARTBEAT_KEY = "queue:scheduler:heartbeat";

    @Mock
    private WaitingQueueRepository waitingQueueRepository;
    @Mock
    private EntryTokenRepository entryTokenRepository;
    @Mock
    private SchedulerLockRepository schedulerLockRepository;
    @Mock
    private EntryTokenGenerator entryTokenGenerator;
    @Mock
    private JitterDelay jitterDelay;
    @Mock
    private EntrySchedulerObservation schedulerObservation;

    @InjectMocks
    private EntrySchedulerService entrySchedulerService;

    @DisplayName("락 획득 실패 시 방출을 수행하지 않는다.")
    @Test
    void releaseEntries_whenLockNotAcquired_shouldSkipRelease() {
        when(schedulerLockRepository.tryAcquireLock(eq(LOCK_KEY), anyString(), eq(5L))).thenReturn(false);

        EntrySchedulerService.ReleaseResult result = entrySchedulerService.releaseEntries(
            EVENT_ID, 18, 300, 5, LOCK_KEY, HEARTBEAT_KEY, 35
        );

        assertThat(result.lockAcquired()).isFalse();
        assertThat(result.releasedCount()).isZero();
        verify(schedulerObservation).onReleaseEntriesInvoked();
        verify(schedulerObservation).onLockNotAcquired();
        verify(schedulerObservation, never()).onTickCompleted(anyInt());
        verify(waitingQueueRepository, never()).popOldest(anyString(), anyLong());
        verify(entryTokenRepository, never()).saveEntryToken(anyLong(), anyString(), anyLong());
        verify(schedulerLockRepository, never()).updateHeartbeat(anyString(), anyString(), anyLong());
    }

    @DisplayName("락 획득 성공 시 배치 방출 후 토큰 발급과 heartbeat를 수행한다.")
    @Test
    void releaseEntries_whenLockAcquired_shouldReleaseAndIssueTokens() {
        when(schedulerLockRepository.tryAcquireLock(eq(LOCK_KEY), anyString(), eq(5L))).thenReturn(true);
        when(waitingQueueRepository.popOldest(EVENT_ID, 18)).thenReturn(List.of(10L, 20L));
        when(entryTokenGenerator.generate()).thenReturn("token-1", "token-2");

        EntrySchedulerService.ReleaseResult result = entrySchedulerService.releaseEntries(
            EVENT_ID, 18, 300, 5, LOCK_KEY, HEARTBEAT_KEY, 35
        );

        assertThat(result.lockAcquired()).isTrue();
        assertThat(result.releasedCount()).isEqualTo(2);
        verify(waitingQueueRepository).popOldest(EVENT_ID, 18);
        verify(entryTokenRepository).saveEntryToken(10L, "token-1", 300L);
        verify(entryTokenRepository).saveEntryToken(20L, "token-2", 300L);
        verify(jitterDelay, times(2)).delay(anyLong());
        verify(schedulerLockRepository).updateHeartbeat(eq(HEARTBEAT_KEY), anyString(), eq(35L));
        verify(schedulerObservation).onReleaseEntriesInvoked();
        verify(schedulerObservation).onTickCompleted(2);
        verify(schedulerObservation, never()).onLockNotAcquired();
    }
}

