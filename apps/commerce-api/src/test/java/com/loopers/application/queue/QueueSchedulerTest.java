package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QueueScheduler 단위 테스트")
class QueueSchedulerTest {

    private WaitingQueueService waitingQueueService;
    private EntryTokenService entryTokenService;
    private ThroughputTracker throughputTracker;
    private SchedulerHealthChecker schedulerHealthChecker;
    private QueueScheduler queueScheduler;

    @BeforeEach
    void setUp() {
        waitingQueueService = mock(WaitingQueueService.class);
        entryTokenService = mock(EntryTokenService.class);
        schedulerHealthChecker = mock(SchedulerHealthChecker.class);
        QueueProperties queueProperties = new QueueProperties(true, 14, 100, 300, 140, 100000);
        throughputTracker = new ThroughputTracker(queueProperties);
        queueScheduler = new QueueScheduler(waitingQueueService, entryTokenService, queueProperties, throughputTracker, schedulerHealthChecker);
    }

    private List<Map.Entry<Long, Double>> withScores(Long... memberIds) {
        double now = System.currentTimeMillis() * 1000.0;
        List<Map.Entry<Long, Double>> result = new java.util.ArrayList<>();
        for (Long id : memberIds) {
            result.add(new AbstractMap.SimpleEntry<>(id, now));
        }
        return result;
    }

    @Test
    @DisplayName("대기열에서 batchSize만큼 꺼내 토큰 발급")
    void issueTokens_popsAndIssues() {
        // given
        when(waitingQueueService.popNWithScore(14)).thenReturn(withScores(1L, 2L, 3L));
        when(entryTokenService.issue(anyLong())).thenReturn("token");

        // when
        queueScheduler.issueTokens();

        // then
        verify(waitingQueueService).popNWithScore(14);
        verify(entryTokenService, times(3)).issue(anyLong());
        verify(entryTokenService).issue(1L);
        verify(entryTokenService).issue(2L);
        verify(entryTokenService).issue(3L);
    }

    @Test
    @DisplayName("대기열 비어있으면 토큰 발급 없음")
    void issueTokens_emptyQueue_noIssue() {
        // given
        when(waitingQueueService.popNWithScore(14)).thenReturn(List.of());

        // when
        queueScheduler.issueTokens();

        // then
        verify(entryTokenService, never()).issue(anyLong());
    }

    @Test
    @DisplayName("queue.enabled=false이면 아무 동작 안 함")
    void issueTokens_disabled_noop() {
        // given
        QueueProperties disabledProperties = new QueueProperties(false, 14, 100, 300, 140, 100000);
        ThroughputTracker disabledTracker = new ThroughputTracker(disabledProperties);
        QueueScheduler disabledScheduler = new QueueScheduler(waitingQueueService, entryTokenService, disabledProperties, disabledTracker, schedulerHealthChecker);

        // when
        disabledScheduler.issueTokens();

        // then
        verify(waitingQueueService, never()).popNWithScore(anyInt());
        verify(entryTokenService, never()).issue(anyLong());
    }

    @Test
    @DisplayName("대기열 인원이 batchSize보다 적으면 있는 만큼만 발급")
    void issueTokens_lessThanBatchSize_issuesAll() {
        // given
        when(waitingQueueService.popNWithScore(14)).thenReturn(withScores(1L, 2L));
        when(entryTokenService.issue(anyLong())).thenReturn("token");

        // when
        queueScheduler.issueTokens();

        // then
        verify(entryTokenService, times(2)).issue(anyLong());
    }
}
