package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.JoinQueueResult;
import com.loopers.domain.queue.QueuePositionSnapshot;
import com.loopers.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueFacadeTest {

    @Mock
    private WaitingQueueService waitingQueueService;

    @Mock
    private EntryTokenRepository entryTokenRepository;

    @Mock
    private QueuePositionProperties queuePositionProperties;

    @Mock
    private QueueFallbackProperties queueFallbackProperties;

    @InjectMocks
    private QueueFacade queueFacade;

    @DisplayName("joinQueue 호출 시 기본 이벤트 ID와 현재 시각 score를 사용한다.")
    @Test
    void joinQueue_shouldUseDefaultEventIdAndCurrentTimestampScore() {
        Long userId = 10L;
        when(queueFallbackProperties.enabled()).thenReturn(true);
        when(waitingQueueService.joinQueue(eq("default"), eq(userId), anyLong(), eq(true)))
            .thenReturn(JoinQueueResult.synced(0L, 1L));

        QueueInfo result = queueFacade.joinQueue(userId);

        ArgumentCaptor<Long> scoreCaptor = ArgumentCaptor.forClass(Long.class);
        verify(waitingQueueService).joinQueue(eq("default"), eq(userId), scoreCaptor.capture(), eq(true));
        Long score = scoreCaptor.getValue();

        assertThat(score).isPositive();
        assertThat(result.position()).isEqualTo(0L);
        assertThat(result.totalWaiting()).isEqualTo(1L);
    }

    @DisplayName("getQueuePosition: 대기열에 있으면 순번·폴링 힌트·예상 대기를 채운다.")
    @Test
    void getQueuePosition_whenInQueue_shouldReturnSnapshot() {
        Long userId = 10L;
        when(waitingQueueService.findPosition(eq("default"), eq(userId)))
                .thenReturn(Optional.of(new QueuePositionSnapshot(5L, 100L)));
        when(entryTokenRepository.findEntryToken(userId)).thenReturn(Optional.empty());
        when(queuePositionProperties.throughputTps()).thenReturn(175.0);

        Optional<QueuePositionInfo> opt = queueFacade.getQueuePosition(userId);

        assertThat(opt).isPresent();
        QueuePositionInfo info = opt.get();
        assertThat(info.position()).isEqualTo(5L);
        assertThat(info.totalWaiting()).isEqualTo(100L);
        assertThat(info.entryToken()).isNull();
        assertThat(info.suggestedPollIntervalMs()).isEqualTo(1000L);
        assertThat(info.retryAfterSeconds()).isEqualTo(1L);
        assertThat(info.estimatedWaitSeconds()).isEqualTo(2L);
    }

    @DisplayName("getQueuePosition: 대기열에 없으면 empty")
    @Test
    void getQueuePosition_whenNotInQueue_shouldReturnEmpty() {
        when(waitingQueueService.findPosition(eq("default"), eq(99L))).thenReturn(Optional.empty());

        assertThat(queueFacade.getQueuePosition(99L)).isEmpty();
    }

    @DisplayName("joinQueue: Redis 실패 시 Kafka 접수 경로면 asyncAccepted 정보를 반환한다.")
    @Test
    void joinQueue_whenAsyncFallback_shouldReturnQueueInfoWithFallbackRequestId() {
        Long userId = 7L;
        when(queueFallbackProperties.enabled()).thenReturn(true);
        when(waitingQueueService.joinQueue(eq("default"), eq(userId), anyLong(), eq(true)))
                .thenReturn(JoinQueueResult.asyncAccepted("fallback-req-abc"));

        QueueInfo info = queueFacade.joinQueue(userId);

        assertThat(info.asyncFallbackPending()).isTrue();
        assertThat(info.fallbackRequestId()).isEqualTo("fallback-req-abc");
        assertThat(info.position()).isNull();
        assertThat(info.totalWaiting()).isNull();
    }
}

