package com.loopers.infrastructure.scheduler;

import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class QueueAdmissionSchedulerTest {

    private QueueAdmissionScheduler scheduler;
    private WaitingQueueRedisRepository waitingQueueRedisRepository;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        waitingQueueRedisRepository = mock(WaitingQueueRedisRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new QueueAdmissionScheduler(waitingQueueRedisRepository, meterRegistry);
    }

    @DisplayName("배치 크기만큼 원자적 POP + 토큰 발급 (Lua)")
    @Test
    void admitUsers_popsAndIssuesTokensAtomically() {
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("1", "2", "3"));

        scheduler.admitUsers();

        verify(waitingQueueRedisRepository).popMinAndIssueTokens(8);
    }

    @DisplayName("빈 큐 → 입장 처리 없음")
    @Test
    void admitUsers_emptyQueue_noAdmission() {
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(Collections.emptyList());

        scheduler.admitUsers();

        verify(waitingQueueRedisRepository).popMinAndIssueTokens(8);
    }

    @DisplayName("8명 배치 크기로 원자적 입장 호출")
    @Test
    void admitUsers_requestsBatchSizeOf8() {
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(Collections.emptyList());

        scheduler.admitUsers();

        verify(waitingQueueRedisRepository).popMinAndIssueTokens(8);
    }

    @DisplayName("타임아웃 정리: 만료 엔트리 제거 호출")
    @Test
    void removeExpiredEntries_callsRepositoryWithCutoff() {
        when(waitingQueueRedisRepository.removeExpiredEntries(anyLong())).thenReturn(5L);

        scheduler.removeExpiredEntries();

        verify(waitingQueueRedisRepository).removeExpiredEntries(anyLong());
    }

    @DisplayName("타임아웃 정리: 제거 대상 없으면 로그 미출력 (정상 동작)")
    @Test
    void removeExpiredEntries_noneExpired_noException() {
        when(waitingQueueRedisRepository.removeExpiredEntries(anyLong())).thenReturn(0L);

        scheduler.removeExpiredEntries();

        verify(waitingQueueRedisRepository).removeExpiredEntries(anyLong());
    }

    // --- 메트릭 검증 ---

    @DisplayName("입장 처리 시 admission 카운터 증가")
    @Test
    void admitUsers_incrementsAdmissionCounter() {
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("1", "2", "3"));

        scheduler.admitUsers();

        double count = meterRegistry.counter("queue.admission.count").count();
        assertThat(count).isEqualTo(3.0);
    }

    @DisplayName("Redis 장애 시 error 카운터 증가")
    @Test
    void admitUsers_redisError_incrementsErrorCounter() {
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenThrow(new RuntimeException("Redis connection failed"));

        scheduler.admitUsers();

        double count = meterRegistry.counter("queue.admission.errors").count();
        assertThat(count).isEqualTo(1.0);
    }

    @DisplayName("타임아웃 정리 시 cleanup 카운터 증가")
    @Test
    void removeExpiredEntries_incrementsCleanupCounter() {
        when(waitingQueueRedisRepository.removeExpiredEntries(anyLong())).thenReturn(5L);

        scheduler.removeExpiredEntries();

        double count = meterRegistry.counter("queue.cleanup.removed").count();
        assertThat(count).isEqualTo(5.0);
    }
}
