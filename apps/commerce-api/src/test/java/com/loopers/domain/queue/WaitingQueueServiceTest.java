package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitingQueueServiceTest {

    private static final String EVENT_ID = "default";
    private static final Long USER_ID = 1L;
    private static final long SCORE = 1000L;
    private static final long CAP = 10_000L;

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @Mock
    private QueueJoinFallbackPublisher queueJoinFallbackPublisher;

    private WaitingQueueService waitingQueueService;

    @BeforeEach
    void setUp() {
        waitingQueueService = new WaitingQueueService(
                waitingQueueRepository,
                Optional.of(queueJoinFallbackPublisher),
                new WaitingQueueCapacityPolicy(CAP)
        );
    }

    @DisplayName("joinQueue 호출 시 순번과 대기인원을 반환한다.")
    @Test
    void joinQueue_withValidInput_shouldReturnPositionAndTotalWaiting() {
        when(waitingQueueRepository.addIfAbsentWithinCapacity(eq(EVENT_ID), eq(USER_ID), eq(SCORE), eq(CAP)))
                .thenReturn(WaitingQueueJoinResult.ADDED);
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID)))
                .thenReturn(Optional.of(new QueuePositionSnapshot(3L, 10L)));

        JoinQueueResult outcome = waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE, true);

        assertThat(outcome.position()).isEqualTo(3L);
        assertThat(outcome.totalWaiting()).isEqualTo(10L);
        assertThat(outcome.asyncFallbackPending()).isFalse();
        verify(waitingQueueRepository).addIfAbsentWithinCapacity(EVENT_ID, USER_ID, SCORE, CAP);
        verify(waitingQueueRepository).findPositionSnapshot(EVENT_ID, USER_ID);
    }

    @DisplayName("중복 진입이어도 현재 순번과 대기인원을 반환한다.")
    @Test
    void joinQueue_withDuplicateUser_shouldReturnCurrentPositionAndTotalWaiting() {
        when(waitingQueueRepository.addIfAbsentWithinCapacity(eq(EVENT_ID), eq(USER_ID), eq(SCORE), eq(CAP)))
                .thenReturn(WaitingQueueJoinResult.ALREADY_MEMBER);
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID)))
                .thenReturn(Optional.of(new QueuePositionSnapshot(1L, 5L)));

        JoinQueueResult outcome = waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE, true);

        assertThat(outcome.position()).isEqualTo(1L);
        assertThat(outcome.totalWaiting()).isEqualTo(5L);
        assertThat(outcome.asyncFallbackPending()).isFalse();
        verify(waitingQueueRepository).addIfAbsentWithinCapacity(EVENT_ID, USER_ID, SCORE, CAP);
        verify(waitingQueueRepository).findPositionSnapshot(EVENT_ID, USER_ID);
    }

    @DisplayName("정원이 찼으면 CONFLICT CoreException을 던진다.")
    @Test
    void joinQueue_whenCapacityFull_shouldThrowConflict() {
        when(waitingQueueRepository.addIfAbsentWithinCapacity(eq(EVENT_ID), eq(USER_ID), eq(SCORE), eq(CAP)))
                .thenReturn(WaitingQueueJoinResult.CAPACITY_FULL);

        assertThatThrownBy(() -> waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE, true))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException ce = (CoreException) ex;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.CONFLICT);
                    assertThat(ce.getCustomMessage()).isEqualTo("대기열 정원이 찼습니다.");
                });
    }

    @DisplayName("Redis 장애 시 Kafka 접수 후 AsyncAccepted를 반환한다.")
    @Test
    void joinQueue_whenRedisDownAndFallbackEnabled_shouldPublishAndReturnAsyncAccepted() {
        when(waitingQueueRepository.addIfAbsentWithinCapacity(eq(EVENT_ID), eq(USER_ID), eq(SCORE), eq(CAP)))
                .thenThrow(new RedisConnectionFailureException("down", new RuntimeException("cause")));

        JoinQueueResult outcome = waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE, true);

        assertThat(outcome.asyncFallbackPending()).isTrue();
        assertThat(outcome.fallbackRequestId()).isNotBlank();
        assertThat(outcome.position()).isNull();
        assertThat(outcome.totalWaiting()).isNull();
        verify(queueJoinFallbackPublisher).publish(eq(EVENT_ID), eq(USER_ID), eq(SCORE), anyString());
    }

    @DisplayName("Redis 장애 시 Kafka 발행이 실패하면 CoreException(INTERNAL_ERROR)으로 정규화한다.")
    @Test
    void joinQueue_whenRedisDownAndKafkaPublishFails_shouldThrowCoreException() {
        when(waitingQueueRepository.addIfAbsentWithinCapacity(eq(EVENT_ID), eq(USER_ID), eq(SCORE), eq(CAP)))
                .thenThrow(new RedisConnectionFailureException("down", new RuntimeException("cause")));
        doThrow(new RuntimeException("broker down"))
                .when(queueJoinFallbackPublisher).publish(anyString(), anyLong(), anyLong(), anyString());

        assertThatThrownBy(() -> waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE, true))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException ce = (CoreException) ex;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
                    assertThat(ce.getCause()).isInstanceOf(RuntimeException.class);
                });
    }

    @DisplayName("findPosition: 순번이 있으면 순번·총 대기 인원을 반환한다.")
    @Test
    void findPosition_whenInQueue_shouldReturnPosition() {
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID)))
                .thenReturn(Optional.of(new QueuePositionSnapshot(2L, 7L)));

        Optional<QueuePositionSnapshot> result = waitingQueueService.findPosition(EVENT_ID, USER_ID);

        assertThat(result).contains(new QueuePositionSnapshot(2L, 7L));
    }

    @DisplayName("findPosition: ZSET에 없으면 empty")
    @Test
    void findPosition_whenNotInQueue_shouldReturnEmpty() {
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID))).thenReturn(Optional.empty());

        Optional<QueuePositionSnapshot> result = waitingQueueService.findPosition(EVENT_ID, USER_ID);

        assertThat(result).isEmpty();
    }

    @DisplayName("진입 직후 스냅샷이 비면(비정상 상태) INTERNAL_ERROR로 실패한다. 재삽입은 하지 않는다.")
    @Test
    void joinQueue_whenSnapshotEmptyAfterAdd_shouldThrowInternalErrorWithoutSecondAdd() {
        when(waitingQueueRepository.addIfAbsentWithinCapacity(eq(EVENT_ID), eq(USER_ID), eq(SCORE), eq(CAP)))
                .thenReturn(WaitingQueueJoinResult.ADDED);
        when(waitingQueueRepository.findPositionSnapshot(eq(EVENT_ID), eq(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitingQueueService.joinQueue(EVENT_ID, USER_ID, SCORE, true))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException ce = (CoreException) ex;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
                });
        verify(waitingQueueRepository).addIfAbsentWithinCapacity(EVENT_ID, USER_ID, SCORE, CAP);
    }
}
