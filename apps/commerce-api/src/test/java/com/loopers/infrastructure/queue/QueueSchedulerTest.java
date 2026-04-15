package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import com.loopers.application.queue.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueSchedulerTest {

    @Mock
    private QueueService queueService;

    @Mock
    private TokenService tokenService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private QueueScheduler queueScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueScheduler, "batchSize", 14);
        ReflectionTestUtils.setField(queueScheduler, "fixedRate", 1L); // jitter = 0ms 고정
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.isHeldByCurrentThread()).willReturn(true);
    }

    @DisplayName("큐가 비어있으면 아무것도 실행하지 않는다.")
    @Test
    void process_emptyQueue() {
        given(queueService.peekBatch(14)).willReturn(List.of());

        queueScheduler.process();

        verify(queueService, never()).remove(org.mockito.ArgumentMatchers.any());
        verify(tokenService, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("큐에 유저가 있으면 즉시 큐에서 제거하고 토큰을 발급한다.")
    @Test
    void process_issueToken() throws InterruptedException {
        given(queueService.peekBatch(14)).willReturn(List.of(1L, 2L));
        given(tokenService.validate(1L)).willReturn(false);
        given(tokenService.validate(2L)).willReturn(false);

        queueScheduler.process();

        verify(queueService).remove(1L);
        verify(queueService).remove(2L);

        Thread.sleep(100); // jitter 비동기 완료 대기
        verify(tokenService).issue(1L);
        verify(tokenService).issue(2L);
    }

    @DisplayName("이미 토큰이 있는 유저는 큐에서만 제거하고 토큰을 재발급하지 않는다.")
    @Test
    void process_skipAlreadyTokenized() throws InterruptedException {
        given(queueService.peekBatch(14)).willReturn(List.of(1L));
        given(tokenService.validate(1L)).willReturn(true);

        queueScheduler.process();

        verify(queueService).remove(1L);

        Thread.sleep(100);
        verify(tokenService, never()).issue(1L);
    }
}
