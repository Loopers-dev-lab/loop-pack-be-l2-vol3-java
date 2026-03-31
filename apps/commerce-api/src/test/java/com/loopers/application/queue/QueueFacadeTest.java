package com.loopers.application.queue;

import com.loopers.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueFacadeTest {

    @Mock
    private WaitingQueueService waitingQueueService;

    @InjectMocks
    private QueueFacade queueFacade;

    @DisplayName("joinQueue 호출 시 기본 이벤트 ID와 현재 시각 score를 사용한다.")
    @Test
    void joinQueue_shouldUseDefaultEventIdAndCurrentTimestampScore() {
        Long userId = 10L;
        when(waitingQueueService.joinQueue(eq("default"), eq(userId), anyLong()))
            .thenReturn(new WaitingQueueService.JoinQueueResult(0L, 1L));

        QueueInfo result = queueFacade.joinQueue(userId);

        ArgumentCaptor<Long> scoreCaptor = ArgumentCaptor.forClass(Long.class);
        verify(waitingQueueService).joinQueue(eq("default"), eq(userId), scoreCaptor.capture());
        Long score = scoreCaptor.getValue();

        assertThat(score).isPositive();
        assertThat(result.position()).isEqualTo(0L);
        assertThat(result.totalWaiting()).isEqualTo(1L);
    }
}

