package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.loopers.support.error.CoreException;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueProperties queueProperties;

    @InjectMocks
    private QueueService queueService;

    @Nested
    @DisplayName("enter — 대기열 진입")
    class Enter {

        @Test
        @DisplayName("대기열이 최대 인원에 도달하면 QUEUE_FULL 예외가 발생한다")
        void queueFull() {
            when(queueRepository.hasValidToken(1L)).thenReturn(false);
            when(queueRepository.getTotalWaiting()).thenReturn(100000L);
            when(queueProperties.getMaxWaitingSize()).thenReturn(100000);

            assertThatThrownBy(() -> queueService.enter(1L))
                    .isInstanceOf(CoreException.class);
            verify(queueRepository, never()).addToWaitingQueue(eq(1L), anyDouble());
        }

        @Test
        @DisplayName("이미 활성 토큰이 있으면 ACTIVE 상태를 반환한다")
        void alreadyActiveToken() {
            when(queueRepository.hasValidToken(1L)).thenReturn(true);

            QueueEntryResult result = queueService.enter(1L);

            assertThat(result.status()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(result.position()).isEqualTo(0);
            verify(queueRepository, never()).addToWaitingQueue(eq(1L), anyDouble());
        }

        @Test
        @DisplayName("새로운 사용자가 대기열에 진입하면 WAITING 상태와 순번을 반환한다")
        void newUserEnters() {
            when(queueRepository.hasValidToken(1L)).thenReturn(false);
            when(queueRepository.getTotalWaiting()).thenReturn(1L);
            when(queueProperties.getMaxWaitingSize()).thenReturn(100000);
            when(queueRepository.addToWaitingQueue(eq(1L), anyDouble())).thenReturn(true);
            when(queueRepository.getPosition(1L)).thenReturn(Optional.of(0L));
            when(queueProperties.getBatchSize()).thenReturn(30);
            when(queueProperties.getSchedulerIntervalMs()).thenReturn(1000L);

            QueueEntryResult result = queueService.enter(1L);

            assertThat(result.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(result.position()).isEqualTo(1);
            verify(queueRepository).addToWaitingQueue(eq(1L), anyDouble());
        }

        @Test
        @DisplayName("이미 대기 중인 사용자가 재진입하면 기존 순번을 유지한다 (ZADD NX)")
        void existingUserReenters() {
            when(queueRepository.hasValidToken(1L)).thenReturn(false);
            when(queueRepository.getTotalWaiting()).thenReturn(10L);
            when(queueProperties.getMaxWaitingSize()).thenReturn(100000);
            when(queueRepository.addToWaitingQueue(eq(1L), anyDouble())).thenReturn(false);
            when(queueRepository.getPosition(1L)).thenReturn(Optional.of(5L));
            when(queueProperties.getBatchSize()).thenReturn(30);
            when(queueProperties.getSchedulerIntervalMs()).thenReturn(1000L);

            QueueEntryResult result = queueService.enter(1L);

            assertThat(result.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(result.position()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("getPosition — 순번 조회")
    class GetPosition {

        @Test
        @DisplayName("활성 토큰이 있으면 ACTIVE 상태와 토큰 잔여 시간을 반환한다")
        void activeToken() {
            when(queueRepository.hasValidToken(1L)).thenReturn(true);
            when(queueRepository.getTokenTtl(1L)).thenReturn(120L);

            QueuePositionResult result = queueService.getPosition(1L);

            assertThat(result.status()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(result.tokenRemainingSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("대기열에 없으면 NOT_IN_QUEUE 상태를 반환한다")
        void notInQueue() {
            when(queueRepository.hasValidToken(1L)).thenReturn(false);
            when(queueRepository.getPosition(1L)).thenReturn(Optional.empty());

            QueuePositionResult result = queueService.getPosition(1L);

            assertThat(result.status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }

        @Test
        @DisplayName("대기 중이면 순번, 대기 인원, 예상 대기 시간, 폴링 주기를 반환한다")
        void waiting() {
            when(queueRepository.hasValidToken(1L)).thenReturn(false);
            when(queueRepository.getPosition(1L)).thenReturn(Optional.of(99L));
            when(queueRepository.getTotalWaiting()).thenReturn(500L);
            when(queueProperties.getBatchSize()).thenReturn(30);
            when(queueProperties.getSchedulerIntervalMs()).thenReturn(1000L);

            QueuePositionResult result = queueService.getPosition(1L);

            assertThat(result.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(result.position()).isEqualTo(100);
            assertThat(result.totalWaiting()).isEqualTo(500);
            assertThat(result.estimatedWaitSeconds()).isGreaterThan(0);
            assertThat(result.nextPollAfterMs()).isEqualTo(3000);
        }
    }

    @Nested
    @DisplayName("activateNextBatch — 배치 활성화")
    class ActivateNextBatch {

        @Test
        @DisplayName("배치 크기만큼 활성화하고 활성화된 수를 반환한다")
        void activatesBatch() {
            when(queueProperties.getBatchSize()).thenReturn(30);
            when(queueProperties.getTokenTtlSeconds()).thenReturn(180);
            when(queueRepository.activateFromQueue(30, 180))
                    .thenReturn(List.of(1L, 2L, 3L));

            int activated = queueService.activateNextBatch();

            assertThat(activated).isEqualTo(3);
            verify(queueRepository).activateFromQueue(30, 180);
        }

        @Test
        @DisplayName("대기열이 비어있으면 0을 반환한다")
        void emptyQueue() {
            when(queueProperties.getBatchSize()).thenReturn(30);
            when(queueProperties.getTokenTtlSeconds()).thenReturn(180);
            when(queueRepository.activateFromQueue(30, 180))
                    .thenReturn(List.of());

            int activated = queueService.activateNextBatch();

            assertThat(activated).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("calculateEstimatedWait — 예상 대기 시간")
    class EstimatedWait {

        @BeforeEach
        void setUp() {
            when(queueProperties.getBatchSize()).thenReturn(30);
            when(queueProperties.getSchedulerIntervalMs()).thenReturn(1000L);
        }

        @Test
        @DisplayName("순번 30이면 예상 대기 1초")
        void position30() {
            int wait = queueService.calculateEstimatedWait(30);
            assertThat(wait).isEqualTo(1);
        }

        @Test
        @DisplayName("순번 300이면 예상 대기 10초")
        void position300() {
            int wait = queueService.calculateEstimatedWait(300);
            assertThat(wait).isEqualTo(10);
        }

        @Test
        @DisplayName("순번 1이면 예상 대기 1초 (올림)")
        void position1() {
            int wait = queueService.calculateEstimatedWait(1);
            assertThat(wait).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("calculateNextPollInterval — 동적 폴링 주기")
    class NextPollInterval {

        @Test
        @DisplayName("순번 1~50이면 1초")
        void frontOfQueue() {
            assertThat(queueService.calculateNextPollInterval(1)).isEqualTo(1000);
            assertThat(queueService.calculateNextPollInterval(50)).isEqualTo(1000);
        }

        @Test
        @DisplayName("순번 51~500이면 3초")
        void midQueue() {
            assertThat(queueService.calculateNextPollInterval(51)).isEqualTo(3000);
            assertThat(queueService.calculateNextPollInterval(500)).isEqualTo(3000);
        }

        @Test
        @DisplayName("순번 501~5000이면 5초")
        void backQueue() {
            assertThat(queueService.calculateNextPollInterval(501)).isEqualTo(5000);
            assertThat(queueService.calculateNextPollInterval(5000)).isEqualTo(5000);
        }

        @Test
        @DisplayName("순번 5001 이상이면 10초")
        void farBackQueue() {
            assertThat(queueService.calculateNextPollInterval(5001)).isEqualTo(10000);
            assertThat(queueService.calculateNextPollInterval(10000)).isEqualTo(10000);
        }
    }
}
