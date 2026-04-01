package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("OrderQueueScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OrderQueueSchedulerTest {

    @Mock private QueueService queueService;
    @Mock private QueueRepository queueRepository;
    @Mock private EntryTokenService entryTokenService;
    @Mock private QueueProperties queueProperties;
    @Mock private RedisTemplate<String, String> redisTemplateMaster;
    @Mock private ValueOperations<String, String> valueOperations;

    private OrderQueueScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderQueueScheduler(
                queueService, queueRepository, entryTokenService,
                queueProperties, redisTemplateMaster
        );
    }

    @Test
    @DisplayName("리더 선출 성공 시 ZPOPMIN 후 토큰을 발급한다")
    void processQueue_LeaderAcquired_ShouldPopAndIssueTokens() {
        // given
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(true);
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
    }

    @Test
    @DisplayName("대기열이 비어있으면 토큰 발급을 시도하지 않는다")
    void processQueue_EmptyQueue_ShouldDoNothing() {
        // given
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(true);
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
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(false);

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
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(true);
        given(queueProperties.getSchedulerBatchSize()).willReturn(14);

        QueueEntry failEntry = new QueueEntry(1L, 1000.0);
        QueueEntry successEntry = new QueueEntry(2L, 1001.0);
        given(queueService.popBatch(14)).willReturn(List.of(failEntry, successEntry));

        doThrow(new RuntimeException("Redis error")).when(entryTokenService).issueToken(1L);

        // when
        scheduler.processQueue();

        // then
        verify(queueRepository).addIfAbsent(1L, 1000.0);
        verify(entryTokenService).issueToken(2L);
    }

    @Test
    @DisplayName("Redis 장애로 리더 선출 실패 시 이번 주기를 skip한다")
    void processQueue_RedisDown_ShouldSkipGracefully() {
        // given
        given(redisTemplateMaster.opsForValue()).willThrow(new RuntimeException("Redis connection refused"));

        // when
        scheduler.processQueue();

        // then
        verify(queueService, never()).popBatch(anyInt());
    }
}
