package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitingQueueServiceTest {

    private static final String EVENT_ID = "default";
    private static final Long USER_ID = 1L;
    private static final long SCORE = 1000L;

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @InjectMocks
    private WaitingQueueService waitingQueueService;

    @DisplayName("joinQueue 호출 시 순번과 대기인원을 반환한다.")
    @Test
    void joinQueue_withValidInput_shouldReturnPositionAndTotalWaiting() {
        when(waitingQueueRepository.addIfAbsent(eq(EVENT_ID), eq(USER_ID), eq(SCORE))).thenReturn(true);
        when(waitingQueueRepository.findRank(eq(EVENT_ID), eq(USER_ID))).thenReturn(Optional.of(3L));
        when(waitingQueueRepository.countWaiting(eq(EVENT_ID))).thenReturn(10L);

        WaitingQueueService.JoinQueueResult result = waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE);

        assertThat(result.position()).isEqualTo(3L);
        assertThat(result.totalWaiting()).isEqualTo(10L);
        verify(waitingQueueRepository).addIfAbsent(EVENT_ID, USER_ID, SCORE);
        verify(waitingQueueRepository).findRank(EVENT_ID, USER_ID);
        verify(waitingQueueRepository).countWaiting(EVENT_ID);
    }

    @DisplayName("중복 진입이어도 현재 순번과 대기인원을 반환한다.")
    @Test
    void joinQueue_withDuplicateUser_shouldReturnCurrentPositionAndTotalWaiting() {
        when(waitingQueueRepository.addIfAbsent(eq(EVENT_ID), eq(USER_ID), eq(SCORE))).thenReturn(false);
        when(waitingQueueRepository.findRank(eq(EVENT_ID), eq(USER_ID))).thenReturn(Optional.of(1L));
        when(waitingQueueRepository.countWaiting(eq(EVENT_ID))).thenReturn(5L);

        WaitingQueueService.JoinQueueResult result = waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE);

        assertThat(result.position()).isEqualTo(1L);
        assertThat(result.totalWaiting()).isEqualTo(5L);
        verify(waitingQueueRepository).addIfAbsent(EVENT_ID, USER_ID, SCORE);
        verify(waitingQueueRepository).findRank(EVENT_ID, USER_ID);
        verify(waitingQueueRepository).countWaiting(EVENT_ID);
    }

    @DisplayName("findPosition: 순번이 있으면 순번·총 대기 인원을 반환한다.")
    @Test
    void findPosition_whenInQueue_shouldReturnPosition() {
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID)))
                .thenReturn(Optional.of(new QueuePositionSnapshot(2L, 7L)));

        Optional<WaitingQueueService.JoinQueueResult> result = waitingQueueService.findPosition(EVENT_ID, USER_ID);

        assertThat(result).contains(new WaitingQueueService.JoinQueueResult(2L, 7L));
    }

    @DisplayName("findPosition: ZSET에 없으면 empty")
    @Test
    void findPosition_whenNotInQueue_shouldReturnEmpty() {
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID))).thenReturn(Optional.empty());

        Optional<WaitingQueueService.JoinQueueResult> result = waitingQueueService.findPosition(EVENT_ID, USER_ID);

        assertThat(result).isEmpty();
    }

    @DisplayName("rank를 찾지 못하면 CoreException을 던진다.")
    @Test
    void joinQueue_whenRankNotFound_shouldThrowCoreException() {
        when(waitingQueueRepository.addIfAbsent(eq(EVENT_ID), eq(USER_ID), eq(SCORE))).thenReturn(true);
        when(waitingQueueRepository.findRank(eq(EVENT_ID), eq(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE))
            .isInstanceOf(CoreException.class);
    }
}

