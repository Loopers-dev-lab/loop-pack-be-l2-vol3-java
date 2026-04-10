package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("OrderQueueScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OrderQueueSchedulerTest {

    @Mock private QueueService queueService;
    @Mock private QueueRepository queueRepository;
    @Mock private EntryTokenService entryTokenService;
    @Mock private QueueProperties queueProperties;
    @Mock private SchedulerLock schedulerLock;

    private OrderQueueScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderQueueScheduler(
                queueService, queueRepository, entryTokenService,
                queueProperties, schedulerLock
        );
    }

    @Test
    @DisplayName("리더 선출 성공 시 ZPOPMIN 후 토큰을 발급하고 락을 해제한다")
    void processQueue_LeaderAcquired_ShouldPopAndIssueTokensAndReleaseLock() {
        // given
        given(schedulerLock.tryAcquire()).willReturn(true);
        given(queueProperties.getSchedulerBatchSize()).willReturn(14);

        List<QueueEntry> entries = List.of(
                new QueueEntry(1L, 1000.0),
                new QueueEntry(2L, 1001.0),
                new QueueEntry(3L, 1002.0)
        );
        given(queueService.popBatch(14)).willReturn(entries);

        // when
        scheduler.processQueue();

        // then
        verify(entryTokenService).issueToken(1L);
        verify(entryTokenService).issueToken(2L);
        verify(entryTokenService).issueToken(3L);
        verify(schedulerLock).release();
    }

    @Test
    @DisplayName("대기열이 비어있으면 토큰 발급을 시도하지 않는다")
    void processQueue_EmptyQueue_ShouldDoNothing() {
        // given
        given(schedulerLock.tryAcquire()).willReturn(true);
        given(queueProperties.getSchedulerBatchSize()).willReturn(14);
        given(queueService.popBatch(14)).willReturn(List.of());

        // when
        scheduler.processQueue();

        // then
        verify(entryTokenService, never()).issueToken(any());
    }

    @Test
    @DisplayName("리더 선출 실패 시 실행하지 않는다")
    void processQueue_LockNotAcquired_ShouldSkip() {
        // given
        given(schedulerLock.tryAcquire()).willReturn(false);

        // when
        scheduler.processQueue();

        // then
        verify(queueService, never()).popBatch(anyInt());
        verify(entryTokenService, never()).issueToken(any());
    }

    @Test
    @DisplayName("토큰 발급 실패 시 원래 score로 대기열에 복귀한다")
    void processQueue_TokenIssueFail_ShouldRequeueUser() {
        // given
        given(schedulerLock.tryAcquire()).willReturn(true);
        given(queueProperties.getSchedulerBatchSize()).willReturn(14);

        QueueEntry failEntry = new QueueEntry(1L, 1000.0);
        QueueEntry successEntry = new QueueEntry(2L, 1001.0);
        given(queueService.popBatch(14)).willReturn(List.of(failEntry, successEntry));

        doThrow(new RuntimeException("Token error")).when(entryTokenService).issueToken(1L);

        // when
        scheduler.processQueue();

        // then
        verify(queueRepository).addIfAbsent(1L, 1000.0);
        verify(entryTokenService).issueToken(2L);
    }

    @Test
    @DisplayName("리더 선출 실패 시 이번 주기를 skip한다")
    void processQueue_LockFailed_ShouldSkipGracefully() {
        // given — SchedulerLock이 false 반환 (Redis 장애 등)
        given(schedulerLock.tryAcquire()).willReturn(false);

        // when
        scheduler.processQueue();

        // then
        verify(queueService, never()).popBatch(anyInt());
    }

    @Test
    @DisplayName("보상 실패 시 다음 주기에 재시도한다")
    void processQueue_CompensateFail_ShouldRetryNextCycle() {
        // given — 1차 실행: 토큰 발급 실패 + 보상도 실패
        given(schedulerLock.tryAcquire()).willReturn(true);
        given(queueProperties.getSchedulerBatchSize()).willReturn(14);

        QueueEntry failEntry = new QueueEntry(1L, 1000.0);
        given(queueService.popBatch(14))
                .willReturn(List.of(failEntry))  // 1차: 1명
                .willReturn(List.of());            // 2차: 빈 배치

        doThrow(new RuntimeException("Token error")).when(entryTokenService).issueToken(1L);

        given(queueRepository.addIfAbsent(1L, 1000.0))
                .willThrow(new RuntimeException("Redis error"))  // 1차 보상 실패
                .willReturn(true);                                // 2차 재시도 성공

        // when — 1차 실행 (보상 실패 → 버퍼에 저장)
        scheduler.processQueue();

        // then — 2차 실행 (재시도 성공)
        scheduler.processQueue();
        verify(queueRepository, times(2)).addIfAbsent(1L, 1000.0);
    }
}
