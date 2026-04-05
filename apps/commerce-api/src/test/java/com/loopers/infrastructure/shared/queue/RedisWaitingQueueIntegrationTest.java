package com.loopers.infrastructure.shared.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.ConcurrentTestHelper;
import com.loopers.support.queue.WaitingQueue;

@DisplayName("RedisWaitingQueue 통합 테스트")
class RedisWaitingQueueIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WaitingQueue waitingQueue;

    @DisplayName("대기열에 진입할 때,")
    @Nested
    class Enter {

        @DisplayName("처음 진입하면, true를 반환한다.")
        @Test
        void returnsTrue_whenFirstEntry() {
            // act
            boolean result = waitingQueue.enter(1L);

            // assert
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(waitingQueue.getTotalCount()).isEqualTo(1)
            );
        }

        @DisplayName("동일 사용자가 재진입하면, false를 반환한다.")
        @Test
        void returnsFalse_whenDuplicateEntry() {
            // arrange
            waitingQueue.enter(1L);

            // act
            boolean result = waitingQueue.enter(1L);

            // assert
            assertAll(
                    () -> assertThat(result).isFalse(),
                    () -> assertThat(waitingQueue.getTotalCount()).isEqualTo(1)
            );
        }
    }

    @DisplayName("순번을 조회할 때,")
    @Nested
    class GetPosition {

        @DisplayName("대기열에 존재하면, 0-based 순번을 반환한다.")
        @Test
        void returnsPosition_whenInQueue() {
            // arrange
            waitingQueue.enter(1L);
            waitingQueue.enter(2L);
            waitingQueue.enter(3L);

            // act & assert
            assertAll(
                    () -> assertThat(waitingQueue.getPosition(1L)).isEqualTo(0),
                    () -> assertThat(waitingQueue.getPosition(2L)).isEqualTo(1),
                    () -> assertThat(waitingQueue.getPosition(3L)).isEqualTo(2)
            );
        }

        @DisplayName("대기열에 없으면, null을 반환한다.")
        @Test
        void returnsNull_whenNotInQueue() {
            // act
            Long position = waitingQueue.getPosition(999L);

            // assert
            assertThat(position).isNull();
        }
    }

    @DisplayName("전체 대기 인원을 조회할 때,")
    @Nested
    class GetTotalCount {

        @DisplayName("대기열이 비어있으면, 0을 반환한다.")
        @Test
        void returnsZero_whenEmpty() {
            // act & assert
            assertThat(waitingQueue.getTotalCount()).isZero();
        }

        @DisplayName("대기열에 사용자가 있으면, 총 인원 수를 반환한다.")
        @Test
        void returnsTotalCount_whenUsersExist() {
            // arrange
            waitingQueue.enter(1L);
            waitingQueue.enter(2L);

            // act & assert
            assertThat(waitingQueue.getTotalCount()).isEqualTo(2);
        }
    }

    @DisplayName("동시에 대기열에 진입할 때,")
    @Nested
    class ConcurrentEnter {

        @DisplayName("100명이 동시에 진입하면, 모두 성공하고 전원 대기열에 존재한다.")
        @Test
        void allSucceed_whenConcurrentEntry() throws InterruptedException {
            // arrange
            int threadCount = 100;
            AtomicLong userIdGenerator = new AtomicLong(1);

            // act
            ConcurrentTestHelper.ConcurrentResult result = ConcurrentTestHelper.executeConcurrently(
                    threadCount, () -> waitingQueue.enter(userIdGenerator.getAndIncrement())
            );

            // assert
            assertAll(
                    () -> assertThat(result.successCount()).isEqualTo(threadCount),
                    () -> assertThat(result.failCount()).isZero(),
                    () -> assertThat(waitingQueue.getTotalCount()).isEqualTo(threadCount)
            );

            // 모든 사용자가 대기열에 존재하는지 검증
            for (long userId = 1; userId <= threadCount; userId++) {
                assertThat(waitingQueue.getPosition(userId)).isNotNull();
            }
        }
    }
}
