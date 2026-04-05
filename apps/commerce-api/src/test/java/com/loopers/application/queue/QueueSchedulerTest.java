package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueSchedulerTest {

    private QueueRepository queueRepository;
    private QueueScheduler queueScheduler;

    @BeforeEach
    void setUp() {
        queueRepository = mock(QueueRepository.class);
        queueScheduler = new QueueScheduler(queueRepository);
    }

    @DisplayName("대기열에 유저가 있으면 꺼내서 토큰을 발급한다")
    @Test
    void activateUsers_success() {
        when(queueRepository.getActiveTokenCount()).thenReturn(0L);
        when(queueRepository.popFromWaitQueue(18)).thenReturn(List.of(1L, 2L, 3L));

        queueScheduler.activateUsers();

        verify(queueRepository).issueToken(eq(1L), anyString(), eq(300L));
        verify(queueRepository).issueToken(eq(2L), anyString(), eq(300L));
        verify(queueRepository).issueToken(eq(3L), anyString(), eq(300L));
        verify(queueRepository).incrementActiveTokenCount(3, 300L);
    }

    @DisplayName("대기열이 비어있으면 토큰을 발급하지 않는다")
    @Test
    void activateUsers_emptyQueue() {
        when(queueRepository.getActiveTokenCount()).thenReturn(0L);
        when(queueRepository.popFromWaitQueue(18)).thenReturn(List.of());

        queueScheduler.activateUsers();

        verify(queueRepository, never()).issueToken(anyLong(), anyString(), anyLong());
    }

    @DisplayName("활성 토큰이 배치 크기 이상이면 추가 발급하지 않는다")
    @Test
    void activateUsers_skipWhenEnoughActiveTokens() {
        when(queueRepository.getActiveTokenCount()).thenReturn(18L);

        queueScheduler.activateUsers();

        verify(queueRepository, never()).popFromWaitQueue(anyLong());
        verify(queueRepository, never()).issueToken(anyLong(), anyString(), anyLong());
    }

    @DisplayName("활성 토큰 부족분만큼만 보충 발급한다")
    @Test
    void activateUsers_supplementDeficit() {
        when(queueRepository.getActiveTokenCount()).thenReturn(13L);
        when(queueRepository.popFromWaitQueue(5)).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));

        queueScheduler.activateUsers();

        verify(queueRepository).popFromWaitQueue(5);
        verify(queueRepository, times(5)).issueToken(anyLong(), anyString(), eq(300L));
        verify(queueRepository).incrementActiveTokenCount(5, 300L);
    }
}
