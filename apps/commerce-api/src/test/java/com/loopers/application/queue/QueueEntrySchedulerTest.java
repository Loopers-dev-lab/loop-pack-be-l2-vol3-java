package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueEntrySchedulerTest {

    @InjectMocks
    private QueueEntryScheduler queueEntryScheduler;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueTokenService queueTokenService;

    @Mock
    private QueueMetrics queueMetrics;

    @DisplayName("대기열에서 배치 크기만큼 꺼내 토큰을 발급한다")
    @Test
    void processQueue_issuesTokensForPoppedUsers() {
        // given
        String eventId = "bf2024";
        when(queueRepository.popFront(eq(eventId), anyInt())).thenReturn(List.of(1L, 2L, 3L));
        when(queueTokenService.issueToken(anyString(), anyLong())).thenReturn("token");

        // when
        queueEntryScheduler.processQueue(eventId);

        // then
        verify(queueTokenService, times(3)).issueToken(eq(eventId), anyLong());
        verify(queueTokenService).issueToken(eventId, 1L);
        verify(queueTokenService).issueToken(eventId, 2L);
        verify(queueTokenService).issueToken(eventId, 3L);
    }

    @DisplayName("대기열이 비어있으면 토큰을 발급하지 않는다")
    @Test
    void processQueue_doesNothingWhenQueueIsEmpty() {
        // given
        String eventId = "bf2024";
        when(queueRepository.popFront(eq(eventId), anyInt())).thenReturn(Collections.emptyList());

        // when
        queueEntryScheduler.processQueue(eventId);

        // then
        verify(queueTokenService, never()).issueToken(anyString(), anyLong());
    }
}
