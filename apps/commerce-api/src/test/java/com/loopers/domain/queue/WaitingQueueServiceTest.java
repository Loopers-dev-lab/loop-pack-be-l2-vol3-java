package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class WaitingQueueServiceTest {

    private InMemoryWaitingQueueRepository waitingQueueRepository;
    private InMemoryEntryTokenRepository entryTokenRepository;
    private WaitingQueueService waitingQueueService;

    @BeforeEach
    void setUp() {
        waitingQueueRepository = new InMemoryWaitingQueueRepository();
        entryTokenRepository = new InMemoryEntryTokenRepository();
        waitingQueueService = new WaitingQueueService(waitingQueueRepository, entryTokenRepository);
    }

    @DisplayName("대기열 진입 시, ")
    @Nested
    class Enter {

        @DisplayName("새로운 유저가 진입하면 순번과 대기 정보가 반환된다.")
        @Test
        void returnsEntryResult_whenNewUser() {
            // act
            QueueEntryResult result = waitingQueueService.enter(1L);

            // assert
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(1),
                    () -> assertThat(result.totalWaiting()).isEqualTo(1),
                    () -> assertThat(result.isNew()).isTrue()
            );
        }

        @DisplayName("여러 유저가 순서대로 진입하면 순번이 올바르게 부여된다.")
        @Test
        void assignsCorrectPositions_whenMultipleUsers() {
            // act
            QueueEntryResult first = waitingQueueService.enter(1L);
            QueueEntryResult second = waitingQueueService.enter(2L);
            QueueEntryResult third = waitingQueueService.enter(3L);

            // assert
            assertAll(
                    () -> assertThat(first.position()).isEqualTo(1),
                    () -> assertThat(second.position()).isEqualTo(2),
                    () -> assertThat(third.position()).isEqualTo(3),
                    () -> assertThat(third.totalWaiting()).isEqualTo(3)
            );
        }

        @DisplayName("이미 대기 중인 유저가 다시 진입하면 isNew가 false이다.")
        @Test
        void returnsNotNew_whenDuplicateEntry() {
            // arrange
            waitingQueueService.enter(1L);

            // act
            QueueEntryResult result = waitingQueueService.enter(1L);

            // assert
            assertAll(
                    () -> assertThat(result.isNew()).isFalse(),
                    () -> assertThat(result.totalWaiting()).isEqualTo(1)
            );
        }
    }

    @DisplayName("순번 조회 시, ")
    @Nested
    class GetPosition {

        @DisplayName("대기 중인 유저의 순번과 예상 대기 시간이 반환된다.")
        @Test
        void returnsWaitingStatus_whenInQueue() {
            // arrange
            waitingQueueService.enter(1L);

            // act
            QueuePositionResult result = waitingQueueService.getPosition(1L);

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo(QueuePositionResult.Status.WAITING),
                    () -> assertThat(result.position()).isEqualTo(1),
                    () -> assertThat(result.totalWaiting()).isEqualTo(1),
                    () -> assertThat(result.estimatedWaitSeconds()).isNotNull()
            );
        }

        @DisplayName("대기열에 없는 유저를 조회하면 NOT_IN_QUEUE 상태가 반환된다.")
        @Test
        void returnsNotInQueue_whenNotEnqueued() {
            // act
            QueuePositionResult result = waitingQueueService.getPosition(999L);

            // assert
            assertThat(result.status()).isEqualTo(QueuePositionResult.Status.NOT_IN_QUEUE);
        }

        @DisplayName("토큰이 발급된 유저를 조회하면 TOKEN_ISSUED 상태와 토큰이 반환된다.")
        @Test
        void returnsTokenIssued_whenTokenExists() {
            // arrange
            entryTokenRepository.issueToken(1L, "test-token-uuid", Duration.ofMinutes(5));

            // act
            QueuePositionResult result = waitingQueueService.getPosition(1L);

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo(QueuePositionResult.Status.TOKEN_ISSUED),
                    () -> assertThat(result.token()).isEqualTo("test-token-uuid")
            );
        }
    }

    @DisplayName("예상 대기 시간 계산 시, ")
    @Nested
    class EstimatedWaitTime {

        @DisplayName("순번 140번이면 예상 대기 시간이 1초이다.")
        @Test
        void returns1Second_whenPosition140() {
            // arrange: 140명 진입
            for (long i = 1; i <= 140; i++) {
                waitingQueueService.enter(i);
            }

            // act
            QueuePositionResult result = waitingQueueService.getPosition(140L);

            // assert
            assertThat(result.estimatedWaitSeconds()).isEqualTo(1);
        }

        @DisplayName("순번 1번이면 예상 대기 시간이 0초이다.")
        @Test
        void returns0Second_whenPosition1() {
            // arrange
            waitingQueueService.enter(1L);

            // act
            QueuePositionResult result = waitingQueueService.getPosition(1L);

            // assert
            assertThat(result.estimatedWaitSeconds()).isEqualTo(0);
        }

        @DisplayName("순번 280번이면 예상 대기 시간이 2초이다.")
        @Test
        void returns2Seconds_whenPosition280() {
            // arrange: 280명 진입
            for (long i = 1; i <= 280; i++) {
                waitingQueueService.enter(i);
            }

            // act
            QueuePositionResult result = waitingQueueService.getPosition(280L);

            // assert
            assertThat(result.estimatedWaitSeconds()).isEqualTo(2);
        }
    }
}
