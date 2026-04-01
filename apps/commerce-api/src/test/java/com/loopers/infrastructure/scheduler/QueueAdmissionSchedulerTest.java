package com.loopers.infrastructure.scheduler;

import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.Mockito.*;

class QueueAdmissionSchedulerTest {

    private QueueAdmissionScheduler scheduler;
    private WaitingQueueRedisRepository waitingQueueRedisRepository;
    private EntryTokenRedisRepository entryTokenRedisRepository;

    @BeforeEach
    void setUp() {
        waitingQueueRedisRepository = mock(WaitingQueueRedisRepository.class);
        entryTokenRedisRepository = mock(EntryTokenRedisRepository.class);
        scheduler = new QueueAdmissionScheduler(waitingQueueRedisRepository, entryTokenRedisRepository);
    }

    @DisplayName("배치 크기만큼 POP하여 토큰 발급")
    @Test
    void admitUsers_popsAndIssuesTokens() {
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(new DefaultTypedTuple<>("1", 1000.0));
        tuples.add(new DefaultTypedTuple<>("2", 1001.0));
        tuples.add(new DefaultTypedTuple<>("3", 1002.0));

        when(waitingQueueRedisRepository.popMin(14)).thenReturn(tuples);

        scheduler.admitUsers();

        verify(entryTokenRedisRepository).issue(1L);
        verify(entryTokenRedisRepository).issue(2L);
        verify(entryTokenRedisRepository).issue(3L);
        verifyNoMoreInteractions(entryTokenRedisRepository);
    }

    @DisplayName("빈 큐 → 토큰 발급 없음")
    @Test
    void admitUsers_emptyQueue_noTokenIssued() {
        when(waitingQueueRedisRepository.popMin(14)).thenReturn(Collections.emptySet());

        scheduler.admitUsers();

        verifyNoInteractions(entryTokenRedisRepository);
    }

    @DisplayName("14명 배치 크기로 ZPOPMIN 호출")
    @Test
    void admitUsers_requestsBatchSizeOf14() {
        when(waitingQueueRedisRepository.popMin(14)).thenReturn(Collections.emptySet());

        scheduler.admitUsers();

        verify(waitingQueueRedisRepository).popMin(14);
    }
}
