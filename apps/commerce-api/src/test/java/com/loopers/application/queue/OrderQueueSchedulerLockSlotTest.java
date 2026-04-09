package com.loopers.application.queue;

import com.loopers.domain.queue.InMemoryEntryTokenRepository;
import com.loopers.domain.queue.InMemoryWaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OrderQueueSchedulerLockSlotTest {

    private InMemoryWaitingQueueRepository waitingQueueRepository;
    private InMemoryEntryTokenRepository entryTokenRepository;
    private OrderQueueReader alwaysEnabled;

    @BeforeEach
    void setUp() {
        waitingQueueRepository = new InMemoryWaitingQueueRepository();
        entryTokenRepository = new InMemoryEntryTokenRepository();
        alwaysEnabled = () -> true;
    }

    @DisplayName("분산 락 동작 시, ")
    @Nested
    class DistributedLock {

        @DisplayName("락을 획득하면 정상적으로 토큰을 발급한다.")
        @Test
        void issuesTokens_whenLockAcquired() {
            // arrange
            OrderQueueScheduler scheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, alwaysEnabled,
                    14, 1000, Duration.ofMinutes(5));
            for (long i = 1; i <= 5; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            scheduler.issueTokens();

            // assert
            for (long i = 1; i <= 5; i++) {
                assertThat(entryTokenRepository.getToken(i)).isPresent();
            }
        }

        @DisplayName("다른 인스턴스가 락을 보유 중이면 토큰을 발급하지 않는다.")
        @Test
        void skipsIssuing_whenLockAlreadyHeld() {
            // arrange
            OrderQueueScheduler scheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, alwaysEnabled,
                    14, 1000, Duration.ofMinutes(5));
            for (long i = 1; i <= 5; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // 다른 인스턴스가 락을 선점
            entryTokenRepository.acquireLock("lock:queue-scheduler", 5000);

            // act
            scheduler.issueTokens();

            // assert: 락을 못 잡았으므로 토큰 발급 없음
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(5);
            for (long i = 1; i <= 5; i++) {
                assertThat(entryTokenRepository.getToken(i)).isEmpty();
            }
        }

        @DisplayName("스케줄러가 실행을 완료하면 락이 해제되어 재실행이 가능하다.")
        @Test
        void releasesLock_afterExecution() {
            // arrange
            OrderQueueScheduler scheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, alwaysEnabled,
                    14, 1000, Duration.ofMinutes(5));
            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act: 연속 두 번 실행
            scheduler.issueTokens();
            scheduler.issueTokens();

            // assert: 두 번 모두 정상 실행됨
            long issuedCount = 0;
            for (long i = 1; i <= 20; i++) {
                if (entryTokenRepository.getToken(i).isPresent()) {
                    issuedCount++;
                }
            }
            assertThat(issuedCount).isGreaterThan(14);
        }
    }

    @DisplayName("슬롯 제한 동작 시, ")
    @Nested
    class SlotLimit {

        @DisplayName("활성 토큰이 maxSlot 미만이면 빈 자리만큼만 발급한다.")
        @Test
        void issuesOnlyAvailableSlots_whenPartiallyFilled() {
            // arrange: maxSlot=100, 이미 95개 토큰 활성화
            int maxSlot = 100;
            OrderQueueScheduler scheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, alwaysEnabled,
                    14, maxSlot, Duration.ofMinutes(5));

            for (long i = 1; i <= 95; i++) {
                entryTokenRepository.issueToken(i, "token-" + i, Duration.ofMinutes(5));
            }
            for (long i = 100; i <= 120; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            scheduler.issueTokens();

            // assert: 5자리만 남았으므로 5명만 발급
            long newlyIssued = 0;
            for (long i = 100; i <= 120; i++) {
                if (entryTokenRepository.getToken(i).isPresent()) {
                    newlyIssued++;
                }
            }
            assertThat(newlyIssued).isEqualTo(5);
        }

        @DisplayName("활성 토큰이 maxSlot 이상이면 추가 발급하지 않는다.")
        @Test
        void doesNotIssue_whenMaxSlotReached() {
            // arrange: maxSlot=50, 이미 50개 토큰 활성화
            int maxSlot = 50;
            OrderQueueScheduler scheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, alwaysEnabled,
                    14, maxSlot, Duration.ofMinutes(5));

            for (long i = 1; i <= 50; i++) {
                entryTokenRepository.issueToken(i, "token-" + i, Duration.ofMinutes(5));
            }
            for (long i = 100; i <= 110; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            scheduler.issueTokens();

            // assert: 대기열에서 꺼내지 않음
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(11);
        }

        @DisplayName("빈 자리가 배치 크기보다 크면 배치 크기만큼만 발급한다.")
        @Test
        void issuesBatchSize_whenSlotsExceedBatchSize() {
            // arrange: maxSlot=1000, 배치 크기=14, 활성 토큰 0
            int maxSlot = 1000;
            OrderQueueScheduler scheduler = new OrderQueueScheduler(
                    waitingQueueRepository, entryTokenRepository, alwaysEnabled,
                    14, maxSlot, Duration.ofMinutes(5));

            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, (double) i);
            }

            // act
            scheduler.issueTokens();

            // assert: 빈 자리 1000이지만 배치 크기 14만큼만 발급
            long issuedCount = 0;
            for (long i = 1; i <= 20; i++) {
                if (entryTokenRepository.getToken(i).isPresent()) {
                    issuedCount++;
                }
            }
            assertThat(issuedCount).isEqualTo(14);
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(6);
        }
    }
}
