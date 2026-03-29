package com.loopers.domain.queue;

import com.loopers.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WaitingQueueService 단위 테스트")
class WaitingQueueServiceTest {

    private WaitingQueueRepository waitingQueueRepository;
    private WaitingQueueService waitingQueueService;

    @BeforeEach
    void setUp() {
        waitingQueueRepository = mock(WaitingQueueRepository.class);
        QueueProperties queueProperties = new QueueProperties(true, 14, 100, 300, 140, 100);
        waitingQueueService = new WaitingQueueService(waitingQueueRepository, queueProperties);
    }

    @Nested
    @DisplayName("enter()")
    class EnterTest {

        @Test
        @DisplayName("대기열 상한 미만이면 진입 성공")
        void enter_underMaxSize_success() {
            // given
            when(waitingQueueRepository.getTotalCount()).thenReturn(50L);
            when(waitingQueueRepository.enter(anyLong(), anyDouble())).thenReturn(true);

            // when
            boolean result = waitingQueueService.enter(1L);

            // then
            assertThat(result).isTrue();
            verify(waitingQueueRepository).enter(anyLong(), anyDouble());
        }

        @Test
        @DisplayName("대기열 상한 도달 시 QUEUE_FULL 예외")
        void enter_atMaxSize_throwsQueueFull() {
            // given
            when(waitingQueueRepository.getTotalCount()).thenReturn(100L);

            // when & then
            assertThatThrownBy(() -> waitingQueueService.enter(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.QUEUE_FULL);

            verify(waitingQueueRepository, never()).enter(anyLong(), anyDouble());
        }

        @Test
        @DisplayName("score에 분산값이 추가됨 (timestamp * 1000 + random)")
        void enter_scoreHasRandomDistribution() {
            // given
            when(waitingQueueRepository.getTotalCount()).thenReturn(0L);
            when(waitingQueueRepository.enter(anyLong(), anyDouble())).thenReturn(true);
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);

            long beforeMs = System.currentTimeMillis();

            // when
            waitingQueueService.enter(1L);

            long afterMs = System.currentTimeMillis();

            // then
            verify(waitingQueueRepository).enter(anyLong(), scoreCaptor.capture());
            double score = scoreCaptor.getValue();
            double lowerBound = beforeMs * 1000.0;
            double upperBound = (afterMs + 1) * 1000.0 + 999;
            assertThat(score).isBetween(lowerBound, upperBound);
        }

        @Test
        @DisplayName("동 ms 진입 시 score가 다를 수 있음 (분산 효과)")
        void enter_sameTimestamp_differentScores() {
            // given
            when(waitingQueueRepository.getTotalCount()).thenReturn(0L);
            when(waitingQueueRepository.enter(anyLong(), anyDouble())).thenReturn(true);
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);

            // when — 100번 진입하여 score 분포 확인
            for (long i = 1; i <= 100; i++) {
                waitingQueueService.enter(i);
            }

            // then
            verify(waitingQueueRepository, org.mockito.Mockito.times(100))
                    .enter(anyLong(), scoreCaptor.capture());
            List<Double> scores = scoreCaptor.getAllValues();
            long distinctCount = scores.stream().distinct().count();
            assertThat(distinctCount).isGreaterThan(1);
        }
    }
}
