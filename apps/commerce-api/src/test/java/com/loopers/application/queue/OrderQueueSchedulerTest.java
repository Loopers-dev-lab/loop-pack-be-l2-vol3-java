package com.loopers.application.queue;

import com.loopers.domain.queue.InMemoryEntryTokenRepository;
import com.loopers.domain.queue.InMemoryWaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderQueueSchedulerTest {

    private InMemoryWaitingQueueRepository waitingQueueRepository;
    private InMemoryEntryTokenRepository entryTokenRepository;
    private OrderQueueScheduler queueScheduler;

    @BeforeEach
    void setUp() {
        waitingQueueRepository = new InMemoryWaitingQueueRepository();
        entryTokenRepository = new InMemoryEntryTokenRepository();
        OrderQueueReader alwaysEnabled = () -> true;
        queueScheduler = new OrderQueueScheduler(waitingQueueRepository, entryTokenRepository, alwaysEnabled);
    }

    @DisplayName("토큰 발급 스케줄러 실행 시, ")
    @Nested
    class IssueTokens {

        @DisplayName("대기열에 유저가 있으면 배치 크기만큼 토큰이 발급된다.")
        @Test
        void issuesTokens_whenUsersInQueue() {
            // arrange
            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            queueScheduler.issueTokens();

            // assert: 14명에게 토큰 발급
            long issuedCount = 0;
            for (long i = 1; i <= 20; i++) {
                if (entryTokenRepository.getToken(i).isPresent()) {
                    issuedCount++;
                }
            }
            assertThat(issuedCount).isEqualTo(14);
        }

        @DisplayName("대기열에 배치 크기보다 적은 유저가 있으면 있는 만큼만 토큰이 발급된다.")
        @Test
        void issuesTokensForAll_whenLessThanBatchSize() {
            // arrange
            for (long i = 1; i <= 5; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            queueScheduler.issueTokens();

            // assert: 5명 전부 토큰 발급
            for (long i = 1; i <= 5; i++) {
                assertThat(entryTokenRepository.getToken(i)).isPresent();
            }
            assertThat(waitingQueueRepository.getTotalCount()).isZero();
        }

        @DisplayName("대기열이 비어있으면 아무 일도 일어나지 않는다.")
        @Test
        void doesNothing_whenQueueIsEmpty() {
            // act
            queueScheduler.issueTokens();

            // assert: 에러 없이 정상 종료
            assertThat(waitingQueueRepository.getTotalCount()).isZero();
        }

        @DisplayName("토큰이 발급된 유저는 대기열에서 제거된다.")
        @Test
        void removesUsersFromQueue_afterTokenIssued() {
            // arrange
            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            queueScheduler.issueTokens();

            // assert: 대기열에 6명 남음 (20 - 14)
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(6);
        }

        @DisplayName("선착순으로 앞에서부터 토큰이 발급된다.")
        @Test
        void issuesTokensInOrder() {
            // arrange
            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            queueScheduler.issueTokens();

            // assert: 1~14번 유저에게 토큰 발급, 15~20번은 미발급
            for (long i = 1; i <= 14; i++) {
                assertThat(entryTokenRepository.getToken(i)).isPresent();
            }

            for (long i = 15; i <= 20; i++) {
                assertThat(entryTokenRepository.getToken(i)).isEmpty();
            }
        }

        @DisplayName("대기열이 비활성화 상태면 토큰을 발급하지 않는다.")
        @Test
        void doesNothing_whenQueueDisabled() {
            // arrange
            OrderQueueReader disabled = () -> false;
            OrderQueueScheduler disabledScheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, disabled);

            for (long i = 1; i <= 5; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            disabledScheduler.issueTokens();

            // assert: 대기열에서 꺼내지 않음
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(5);
        }
    }
}
